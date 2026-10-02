package com.music.recommendation.service.recommendation;

import com.music.recommendation.config.RecommenderProperties;
import com.music.recommendation.entity.Song;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 自适应混合推荐引擎
 *
 * 核心改进（对比固定权重版）：
 *   1. 五档分档：按用户交互数 (0 / 4 / 11 / 31 / 101+) 自适应调整权重
 *   2. 动态因子：CF 返回 < N 首时自动降 CF → 转移给 CB+popularity
 *   3. 活跃度修正：7 天内活跃 → CF 加权；30 天沉寂 → CF 降权
 *   4. 全部权重阈值可在 application.yml 调整
 *
 * 论文可拓展：对比"固定权重 vs 自适应权重"的 Precision@K / Recall@K 差异
 */
@Service
@EnableConfigurationProperties(RecommenderProperties.class)
public class HybridRecommenderServiceImpl implements HybridRecommenderService {

    private static final Logger log = LoggerFactory.getLogger(HybridRecommenderServiceImpl.class);
    private static final Random RANDOM = new Random();

    private final CollaborativeRecommender collaborativeRecommender;
    private final AlsRecommender alsRecommender;
    private final ContentBasedRecommender contentBasedRecommender;
    private final SongRepository songRepository;
    private final UserSongInteractionRepository interactionRepository;
    private final RecommenderProperties props;

    public HybridRecommenderServiceImpl(CollaborativeRecommender collaborativeRecommender,
                                        AlsRecommender alsRecommender,
                                        ContentBasedRecommender contentBasedRecommender,
                                        SongRepository songRepository,
                                        UserSongInteractionRepository interactionRepository,
                                        RecommenderProperties props) {
        this.collaborativeRecommender = collaborativeRecommender;
        this.alsRecommender = alsRecommender;
        this.contentBasedRecommender = contentBasedRecommender;
        this.songRepository = songRepository;
        this.interactionRepository = interactionRepository;
        this.props = props;
    }

    @Override
    public List<Song> recommend(Long userId, int limit) {
        long interactionCount = interactionRepository.countByUserId(userId);
        if (interactionCount == 0) {
            return getColdStartRecommendations(limit);
        }

        // === Step 1: 根据交互数量选基础档权重 ===
        RecommenderProperties.Tier tier = pickTier(interactionCount);

        // === Step 2: 先跑 CF/ALS/CB，结果数量出来后再应用动态调整 ===
        Set<Long> interactedSongIds = new HashSet<>(interactionRepository.findSongIdsByUserId(userId));
        int candidateLimit = limit * 3;
        List<Song> collaborativeSongs = collaborativeRecommender.recommend(userId, candidateLimit);
        List<Song> alsSongs = alsRecommender.recommend(userId, candidateLimit);
        List<Song> contentBasedSongs = contentBasedRecommender.recommend(userId, candidateLimit);
        List<Song> popularitySongs = getPopularityBasedRecommendations(candidateLimit, interactedSongIds);
        List<Song> exploreSongs = getExploreRecommendations(candidateLimit, interactedSongIds);

        if (collaborativeSongs.isEmpty() && alsSongs.isEmpty() && contentBasedSongs.isEmpty()
                && popularitySongs.isEmpty() && exploreSongs.isEmpty()) {
            return getColdStartRecommendations(limit);
        }

        // === Step 3: 计算最终权重（基础档 + 动态因子） ===
        WeightResult weights = computeFinalWeights(tier, userId, collaborativeSongs);

        log.info("[HybridAdaptive] userId={} interactions={} tierMin={} " +
                        "→ CF={} ALS={} CB={} Pop={} Exp={} | cfSize={} alsSize={} cbSize={}",
                userId, interactionCount, tier.getMinInteractions(),
                String.format("%.2f", weights.cf),
                String.format("%.2f", weights.als),
                String.format("%.2f", weights.cb),
                String.format("%.2f", weights.popularity),
                String.format("%.2f", weights.explore),
                collaborativeSongs.size(), alsSongs.size(), contentBasedSongs.size());

        // === Step 4: 统一归一化加权融合 ===
        Map<Long, Double> scoreMap = new HashMap<>();
        rankAndScore(collaborativeSongs, weights.cf, candidateLimit, scoreMap);
        rankAndScore(alsSongs, weights.als, candidateLimit, scoreMap);
        rankAndScore(contentBasedSongs, weights.cb, candidateLimit, scoreMap);
        rankAndScore(popularitySongs, weights.popularity, candidateLimit, scoreMap);
        rankAndScore(exploreSongs, weights.explore, candidateLimit, scoreMap);

        // === Step 5: 批量加载 + 补齐（同旧版逻辑） ===
        List<Long> topIds = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit * 2)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, Song> songMap = songRepository.findAllById(topIds).stream()
                .collect(Collectors.toMap(Song::getId, s -> s));

        List<Song> results = topIds.stream()
                .map(songMap::get)
                .filter(Objects::nonNull)
                .limit(limit)
                .collect(Collectors.toList());

        if (results.size() < limit) {
            Set<Long> existingIds = results.stream().map(Song::getId).collect(Collectors.toSet());
            for (Map.Entry<Long, Double> e : scoreMap.entrySet()) {
                if (!existingIds.contains(e.getKey()) && songMap.containsKey(e.getKey())) {
                    results.add(songMap.get(e.getKey()));
                    existingIds.add(e.getKey());
                }
                if (results.size() >= limit) break;
            }
            if (results.size() < limit) {
                List<Long> allIds = new ArrayList<>(songMap.keySet());
                List<Long> remaining = allIds.stream()
                        .filter(id -> !results.stream().map(Song::getId).collect(Collectors.toSet()).contains(id))
                        .collect(Collectors.toList());
                Collections.shuffle(remaining, RANDOM);
                List<Song> extra = songRepository.findAllById(remaining.stream().limit(limit - results.size()).collect(Collectors.toList()));
                results.addAll(extra);
            }
        }

        return results;
    }

    // ==================== 核心新增：自适应权重计算 ====================

    /**
     * 从配置列表中选档：取 minInteractions ≤ interactionCount 的最后一档
     * YAML 档位必须按 min-interactions 升序排列
     */
    private RecommenderProperties.Tier pickTier(long interactionCount) {
        List<RecommenderProperties.Tier> tiers = props.getAdaptive().getTiers();
        if (tiers == null || tiers.isEmpty()) {
            return new RecommenderProperties.Tier(); // fallback all zeros
        }
        RecommenderProperties.Tier selected = tiers.get(0);
        for (RecommenderProperties.Tier t : tiers) {
            if (interactionCount >= t.getMinInteractions()) {
                selected = t;
            } else {
                break;
            }
        }
        return selected;
    }

    /**
     * 最终权重 = 基础档 + CF/ALS 拆分 + 动态因子 + 活跃度 → 归一化
     *
     * 5 路融合: CF(邻居) + ALS(隐因子) + CB(内容) + Popularity(热度) + Explore(探索)
     * CF 与 ALS 之间是互补关系：
     *   - CF 基于邻居，能利用显式相似性，小用户群效果好
     *   - ALS 基于隐因子矩阵分解，能捕捉低秩泛化模式
     *   - 两者权重和 = 配置中的 cf（协同总权重）
     *   - 默认 CF:ALS = 60:40；CF 空结果时向 ALS 倾斜
     */
    private WeightResult computeFinalWeights(RecommenderProperties.Tier tier,
                                             Long userId,
                                             List<Song> cfSongs) {
        double cfTotal = tier.getCf();
        double cb = tier.getCb();
        double pop = tier.getPopularity();
        double exp = tier.getExplore();

        // === 先拆分 CF 为 CF邻居 + ALS隐因子 ===
        double cfRatio = 0.60, alsRatio = 0.40;
        if (cfSongs.size() < props.getDynamic().getCfMinResults()) {
            // CF 空结果 → 向 ALS 倾斜（ALS 对稀疏更鲁棒）
            cfRatio = 0.30; alsRatio = 0.70;
            log.debug("[HybridAdaptive] CF empty, shift ratio CF:ALS 0.60:0.40 → 0.30:0.70");
        }
        double cf = cfTotal * cfRatio;
        double als = cfTotal * alsRatio;

        // === 动态因子 ===
        // CF 空结果惩罚（已经在上面通过调整 CF:ALS 比例解决大部分）
        // 但如果 ALS 也没结果，再惩罚总协同权重
        double cfEmptyPenalty = 0;
        if (cfSongs.size() < props.getDynamic().getCfMinResults()) {
            cfEmptyPenalty = props.getDynamic().getCfEmptyPenalty();
            cf = cf * (1 - cfEmptyPenalty);
            als = als * (1 - cfEmptyPenalty * 0.7); // ALS 只惩罚 70%
            double shift = cfTotal * (cfRatio * cfEmptyPenalty + alsRatio * cfEmptyPenalty * 0.7);
            cb += shift * 0.6;
            pop += shift * 0.4;
        }

        // === 活跃度修正 ===
        Optional<LocalDateTime> lastOpt = interactionRepository.findLastInteractionTime(userId);
        if (lastOpt.isPresent()) {
            long daysAgo = ChronoUnit.DAYS.between(lastOpt.get(), LocalDateTime.now());
            if (daysAgo <= props.getDynamic().getActiveDays()) {
                // 活跃：总协同信号加权（CF 和 ALS 同时受益）
                double boost = props.getDynamic().getCfBoostActive();
                cf = Math.min(cf + boost * cfRatio, 0.60);
                als = Math.min(als + boost * alsRatio, 0.60);
                exp = Math.max(exp - boost * 0.5, 0.02);
            } else if (daysAgo >= props.getDynamic().getInactiveDays()) {
                // 沉寂：降总协同，提升探索
                double penalty = props.getDynamic().getCfPenaltyInactive();
                cf = Math.max(cf - penalty * cfRatio, 0.02);
                als = Math.max(als - penalty * alsRatio, 0.02);
                exp += penalty * 0.7;
            }
        }

        // === 归一化到 1.0 ===
        double total = cf + als + cb + pop + exp;
        if (total > 0) {
            cf /= total; als /= total; cb /= total; pop /= total; exp /= total;
        }

        return new WeightResult(cf, als, cb, pop, exp);
    }

    private static class WeightResult {
        final double cf, als, cb, popularity, explore;
        WeightResult(double cf, double als, double cb, double popularity, double explore) {
            this.cf = cf; this.als = als; this.cb = cb; this.popularity = popularity; this.explore = explore;
        }
    }

    // ==================== 以下同旧版（无改动） ====================

    private void rankAndScore(List<Song> songs, double weight, int fixedNorm, Map<Long, Double> scoreMap) {
        if (songs == null || songs.isEmpty()) return;
        int norm = Math.max(fixedNorm, 1);
        for (int i = 0; i < songs.size(); i++) {
            double rankFactor = Math.max(0.0, 1.0 - (double) i / norm);
            scoreMap.merge(songs.get(i).getId(), weight * rankFactor, Double::sum);
        }
    }

    private List<Song> getColdStartRecommendations(int limit) {
        double hotRatio = props.getAdaptive().getColdStartHotRatio();
        int hotCount = Math.max(1, (int) (limit * hotRatio));
        int totalSongs = (int) songRepository.count();
        if (totalSongs == 0) return Collections.emptyList();
        int batch = Math.min(totalSongs, limit * 4);
        List<Song> batchSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, batch));
        List<Song> hotSongs = new ArrayList<>(batchSongs.subList(0, Math.min(hotCount, batchSongs.size())));
        Set<Long> usedIds = hotSongs.stream().map(Song::getId).collect(Collectors.toSet());
        List<Song> remaining = new ArrayList<>(batchSongs.subList(hotCount, batchSongs.size()));
        Collections.shuffle(remaining, RANDOM);
        List<Song> result = new ArrayList<>(hotSongs);
        for (Song song : remaining) {
            if (!usedIds.contains(song.getId())) {
                result.add(song);
                usedIds.add(song.getId());
            }
            if (result.size() >= limit) break;
        }
        List<Song> mixed = new ArrayList<>(result);
        if (mixed.size() > 2) {
            List<Song> tail = new ArrayList<>(mixed.subList(2, mixed.size()));
            Collections.shuffle(tail, RANDOM);
            mixed = new ArrayList<>(mixed.subList(0, 2));
            mixed.addAll(tail);
        }
        return mixed;
    }

    private List<Song> getPopularityBasedRecommendations(int limit, Set<Long> interactedSongIds) {
        List<Song> popular = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, limit + interactedSongIds.size()));
        return popular.stream()
                .filter(s -> !interactedSongIds.contains(s.getId()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<Song> getExploreRecommendations(int limit, Set<Long> interactedSongIds) {
        int total = (int) songRepository.count();
        if (total == 0) return Collections.emptyList();
        int batch = Math.min(total, (limit + interactedSongIds.size()) * 6);
        List<Song> batchSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, batch));
        List<Song> candidates = batchSongs.stream()
                .filter(s -> !interactedSongIds.contains(s.getId()))
                .collect(Collectors.toList());
        Collections.shuffle(candidates, RANDOM);
        return candidates.stream().limit(limit).collect(Collectors.toList());
    }
}
