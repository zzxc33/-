package com.music.recommendation.service.recommendation;

import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.UserSongInteraction;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强型协同过滤推荐引擎
 *
 * 融合两种协同过滤策略：
 * 1. 基于用户的协同过滤 (User-Based CF)：找到相似用户，推荐他们喜欢的歌曲
 * 2. 基于物品的协同过滤 (Item-Based CF)：找到相似歌曲，推荐与用户历史偏好相似的歌曲
 *
 * 相似度计算使用皮尔逊相关系数 + Jaccard相似度 + 交互强度加权
 * 评分预测考虑：播放次数、点赞、播放时长（通过播放次数近似）、时效性
 */
@Component
public class CollaborativeRecommender {

    private final UserSongInteractionRepository interactionRepository;
    private final SongRepository songRepository;

    public CollaborativeRecommender(UserSongInteractionRepository interactionRepository,
                                    SongRepository songRepository) {
        this.interactionRepository = interactionRepository;
        this.songRepository = songRepository;
    }

    /** 相似用户数量 */
    private static final int SIMILAR_USER_COUNT = 10;
    /** 相似歌曲数量（物品协同过滤用） */
    private static final int SIMILAR_SONG_COUNT = 20;
    /** 用户协同过滤权重 */
    private static final double USER_CF_WEIGHT = 0.6;
    /** 物品协同过滤权重 */
    private static final double ITEM_CF_WEIGHT = 0.4;

    /**
     * 为用户生成协同过滤推荐
     * @param userId 用户ID
     * @param limit 推荐数量
     * @return 推荐歌曲列表（按预测评分排序）
     */
    public List<Song> recommend(Long userId, int limit) {
        // 获取用户已交互歌曲ID
        Set<Long> userSongIds = new HashSet<>(interactionRepository.findSongIdsByUserId(userId));

        // 如果用户没有交互记录，返回空列表（由上层做冷启动处理）
        if (userSongIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 基于用户的协同过滤
        Map<Long, Double> userBasedScores = getUserBasedRecommendations(userId, userSongIds, limit);

        // 2. 基于物品的协同过滤
        Map<Long, Double> itemBasedScores = getItemBasedRecommendations(userId, userSongIds, limit);

        // 3. 融合两种策略的评分
        Map<Long, Double> finalScores = new HashMap<>();
        for (Map.Entry<Long, Double> entry : userBasedScores.entrySet()) {
            finalScores.merge(entry.getKey(), entry.getValue() * USER_CF_WEIGHT, Double::sum);
        }
        for (Map.Entry<Long, Double> entry : itemBasedScores.entrySet()) {
            finalScores.merge(entry.getKey(), entry.getValue() * ITEM_CF_WEIGHT, Double::sum);
        }

        // 4. 按分数排序返回
        return finalScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> songRepository.findById(entry.getKey()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ==================== 基于用户的协同过滤 ====================

    /**
     * 基于用户的协同过滤推荐
     * 1. 找到与目标用户兴趣最相似的 N 个用户
     * 2. 根据相似用户的喜好加权预测目标用户对未听歌曲的评分
     */
    private Map<Long, Double> getUserBasedRecommendations(Long userId, Set<Long> userSongIds, int limit) {
        // 1. 获取所有其他用户（或通过 batch 获取相似用户候选）
        List<Long> allUserIds = getAllUserIdsExcluding(userId);
        if (allUserIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 2. 批量加载所有用户的交互数据，避免 N+1 查询
        Map<Long, List<UserSongInteraction>> allUsersInteractions = batchLoadInteractions(allUserIds);

        // 3. 计算目标用户与每个其他用户的皮尔逊相似度
        List<UserSimilarity> similarities = new ArrayList<>();
        List<UserSongInteraction> targetInteractions = interactionRepository.findByUserId(userId);

        for (Long otherUserId : allUserIds) {
            List<UserSongInteraction> otherInteractions = allUsersInteractions.get(otherUserId);
            if (otherInteractions == null || otherInteractions.isEmpty()) continue;

            double similarity = computePearsonSimilarity(targetInteractions, otherInteractions);
            if (similarity > 0) {
                similarities.add(new UserSimilarity(otherUserId, similarity));
            }
        }

        // 4. 按相似度排序，取前 N 个
        similarities.sort((a, b) -> Double.compare(b.similarity, a.similarity));
        List<UserSimilarity> topSimilarUsers = similarities.subList(0, Math.min(SIMILAR_USER_COUNT, similarities.size()));

        if (topSimilarUsers.isEmpty()) {
            return Collections.emptyMap();
        }

        // 5. 加权预测评分：对相似用户喜欢的但目标用户没听过的歌曲进行评分预测
        Map<Long, Double> predictedScores = new HashMap<>();
        Map<Long, Double> similaritySum = new HashMap<>();

        // 归一化相似度权重
        double totalSimilarity = topSimilarUsers.stream()
                .mapToDouble(u -> u.similarity).sum();
        if (totalSimilarity <= 0) return Collections.emptyMap();

        for (UserSimilarity similarUser : topSimilarUsers) {
            double normalizedWeight = similarUser.similarity / totalSimilarity;
            List<UserSongInteraction> similarUserInteractions = allUsersInteractions.get(similarUser.userId);

            for (UserSongInteraction interaction : similarUserInteractions) {
                Long songId = interaction.getSongId();
                if (userSongIds.contains(songId)) continue; // 跳过用户已听过的歌曲

                // 计算用户对这首歌的偏好评分
                double preference = computeUserPreference(interaction);
                predictedScores.merge(songId, normalizedWeight * preference, Double::sum);
                similaritySum.merge(songId, normalizedWeight, Double::sum);
            }
        }

        // 6. 计算最终加权评分
        Map<Long, Double> finalScores = new HashMap<>();
        for (Map.Entry<Long, Double> entry : predictedScores.entrySet()) {
            double sumSim = similaritySum.getOrDefault(entry.getKey(), 1.0);
            finalScores.put(entry.getKey(), entry.getValue() / sumSim);
        }

        return finalScores;
    }

    // ==================== 基于物品的协同过滤 ====================

    /**
     * 基于物品的协同过滤推荐
     * 1. 对用户听过的每首歌，找到与之最相似的歌曲
     * 2. 根据用户对已听歌曲的偏好加权推荐相似歌曲
     */
    private Map<Long, Double> getItemBasedRecommendations(Long userId, Set<Long> userSongIds, int limit) {
        if (userSongIds.isEmpty()) return Collections.emptyMap();

        // 1. 获取用户对所有已交互歌曲的偏好评分
        List<UserSongInteraction> userInteractions = interactionRepository.findByUserId(userId);
        Map<Long, Double> userPreferenceMap = new HashMap<>();
        for (UserSongInteraction interaction : userInteractions) {
            userPreferenceMap.put(interaction.getSongId(), computeUserPreference(interaction));
        }

        // 2. 对每个用户已交互的歌曲，找到最相似的歌曲
        Map<Long, Double> candidateScores = new HashMap<>();

        for (Long interactedSongId : userSongIds) {
            double userPref = userPreferenceMap.getOrDefault(interactedSongId, 1.0);
            if (userPref <= 0) continue;

            // 获取与当前歌曲相似的歌曲
            Map<Long, Double> similarSongs = findSimilarSongs(interactedSongId, userSongIds, SIMILAR_SONG_COUNT);

            for (Map.Entry<Long, Double> similarEntry : similarSongs.entrySet()) {
                Long similarSongId = similarEntry.getKey();
                double similarity = similarEntry.getValue();

                // 用用户对原歌曲的偏好 × 歌曲相似度 作为预测评分
                double score = userPref * similarity;
                candidateScores.merge(similarSongId, score, Double::sum);
            }
        }

        return candidateScores;
    }

    /**
     * 找与指定歌曲最相似的歌曲
     * 相似度基于：同时喜欢这两首歌的用户比例（协同过滤思想）
     * 优化：批量加载所有相关用户的交互记录，避免 N+1
     */
    private Map<Long, Double> findSimilarSongs(Long songId, Set<Long> excludeSongIds, int limit) {
        // 1. 获取喜欢/听过这首歌的所有用户
        List<Long> usersWhoLikedSong = interactionRepository.findUserIdsByLikedSongId(songId);

        // 再加入播放过这首歌的用户
        List<UserSongInteraction> allInteractionsForSong = interactionRepository.findBySongId(songId);
        Set<Long> usersWhoInteracted = allInteractionsForSong.stream()
                .map(UserSongInteraction::getUserId)
                .collect(Collectors.toSet());
        // 合并
        Set<Long> relevantUsers = new HashSet<>(usersWhoLikedSong);
        relevantUsers.addAll(usersWhoInteracted);

        if (relevantUsers.isEmpty()) return Collections.emptyMap();

        // 2. 批量加载所有相关用户的交互记录（单次 SQL）
        List<UserSongInteraction> batchInteractions = interactionRepository.findByUserIdIn(relevantUsers);

        // 3. 统计这些用户还听过/喜欢哪些其他歌曲
        Map<Long, Double> coOccurrence = new HashMap<>();
        for (UserSongInteraction interaction : batchInteractions) {
            Long otherSongId = interaction.getSongId();
            if (otherSongId.equals(songId) || excludeSongIds.contains(otherSongId)) continue;

            double weight = computeUserPreference(interaction);
            coOccurrence.merge(otherSongId, weight, Double::sum);
        }

        if (coOccurrence.isEmpty()) return Collections.emptyMap();

        // 4. 计算归一化相似度（共同用户数 / sqrt(被推荐歌曲用户数 × 候选歌曲用户数)）
        double sqrtUsersA = Math.sqrt(relevantUsers.size());
        Map<Long, Double> similarityMap = new HashMap<>();

        for (Map.Entry<Long, Double> entry : coOccurrence.entrySet()) {
            Long candidateSongId = entry.getKey();
            double coCount = entry.getValue();
            double sqrtUsersB = Math.sqrt(1 + coCount); // 近似估计
            double similarity = coCount / (sqrtUsersA * sqrtUsersB);
            similarityMap.put(candidateSongId, similarity);
        }

        // 5. 按相似度排序取 top N
        return similarityMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    // ==================== 相似度计算 ====================

    /**
     * 计算皮尔逊相关系数（基于用户对共同歌曲的偏好评分）
     */
    double computePearsonSimilarity(List<UserSongInteraction> userAInteractions,
                                    List<UserSongInteraction> userBInteractions) {
        // 构建歌曲ID到评分的映射
        Map<Long, Double> ratingsA = buildRatingMap(userAInteractions);
        Map<Long, Double> ratingsB = buildRatingMap(userBInteractions);

        // 找到共同评分的歌曲
        List<Long> commonSongs = new ArrayList<>(ratingsA.keySet());
        commonSongs.retainAll(ratingsB.keySet());

        if (commonSongs.size() < 2) {
            // 如果共同歌曲太少，退化为 Jaccard 相似度
            return computeJaccardSimilarity(
                    new HashSet<>(ratingsA.keySet()),
                    new HashSet<>(ratingsB.keySet())
            );
        }

        int n = commonSongs.size();

        // 计算均值
        double meanA = commonSongs.stream().mapToDouble(ratingsA::get).average().orElse(0);
        double meanB = commonSongs.stream().mapToDouble(ratingsB::get).average().orElse(0);

        // 计算皮尔逊相关系数
        double numerator = 0;
        double denomA = 0;
        double denomB = 0;

        for (Long songId : commonSongs) {
            double diffA = ratingsA.get(songId) - meanA;
            double diffB = ratingsB.get(songId) - meanB;
            numerator += diffA * diffB;
            denomA += diffA * diffA;
            denomB += diffB * diffB;
        }

        double denominator = Math.sqrt(denomA) * Math.sqrt(denomB);
        if (denominator == 0) return 0;

        double pearson = numerator / denominator;

        // 结合 Jaccard 相似度进行平滑（共同歌曲越多，皮尔逊越可信）
        double jaccard = computeJaccardSimilarity(
                new HashSet<>(ratingsA.keySet()),
                new HashSet<>(ratingsB.keySet())
        );

        double jaccardWeight = Math.min(1.0, (double) n / 10.0); // 共同歌曲达到10首时权重最大

        return pearson * jaccardWeight + jaccard * (1 - jaccardWeight);
    }

    /**
     * 构建用户评分映射（考虑播放次数、点赞、时效性）
     */
    private Map<Long, Double> buildRatingMap(List<UserSongInteraction> interactions) {
        Map<Long, Double> ratingMap = new HashMap<>();
        for (UserSongInteraction interaction : interactions) {
            ratingMap.put(interaction.getSongId(), computeUserPreference(interaction));
        }
        return ratingMap;
    }

    /**
     * 计算用户对某首歌的综合偏好评分（0~5 分）
     */
    double computeUserPreference(UserSongInteraction interaction) {
        double score = 1.0; // 基础分：听过

        // 播放次数加分（log 平滑）
        int playCount = interaction.getPlayCount() != null ? interaction.getPlayCount() : 0;
        score += Math.log1p(playCount) * 0.8;

        // 点赞加分
        if (interaction.getIsLiked() != null && interaction.getIsLiked()) {
            score += 2.0;
        }

        // 时效性加分（最近播放的歌曲权重更高）
        if (interaction.getLastPlayedAt() != null) {
            long daysSincePlayed = ChronoUnit.DAYS.between(interaction.getLastPlayedAt(), LocalDateTime.now());
            double recencyBoost = Math.max(0, 1.0 - daysSincePlayed / 30.0); // 30天内逐渐衰减
            score += recencyBoost * 0.5;
        }

        return Math.min(5.0, score); // 最高5分
    }

    /**
     * 计算 Jaccard 相似度
     * J(A, B) = |A ∩ B| / |A ∪ B|
     */
    double computeJaccardSimilarity(Set<Long> setA, Set<Long> setB) {
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;

        Set<Long> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<Long> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }

    // ==================== 工具方法 ====================

    /**
     * 获取除目标用户外的所有用户ID
     */
    private List<Long> getAllUserIdsExcluding(Long userId) {
        // 通过交互记录获取活跃用户
        List<Long> userIds = interactionRepository.findSimilarUserIds(userId);
        return userIds.stream()
                .filter(id -> !id.equals(userId))
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 批量加载用户的交互数据（单次 SQL 查询 + 内存分组，解决 N+1）
     */
    private Map<Long, List<UserSongInteraction>> batchLoadInteractions(List<Long> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();

        // 单次批量查询：WHERE userId IN (...)
        List<UserSongInteraction> allInteractions = interactionRepository.findByUserIdIn(userIds);

        // 内存分组
        Map<Long, List<UserSongInteraction>> result = new HashMap<>();
        for (UserSongInteraction interaction : allInteractions) {
            result.computeIfAbsent(interaction.getUserId(), k -> new ArrayList<>())
                  .add(interaction);
        }
        return result;
    }

    // ==================== 内部数据结构 ====================

    /** 用户相似度 */
    private static class UserSimilarity {
        final Long userId;
        final double similarity;

        UserSimilarity(Long userId, double similarity) {
            this.userId = userId;
            this.similarity = similarity;
        }
    }
}