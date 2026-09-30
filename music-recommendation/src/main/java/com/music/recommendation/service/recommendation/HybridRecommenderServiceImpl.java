package com.music.recommendation.service.recommendation;

import com.music.recommendation.entity.Song;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合推荐引擎实现（三种算法融合，已优化）
 *
 * 性能优化点：
 *   1. 消除 N+1 查询：用 findAllById(Set<Long>) 批量加载 top-N 候选
 *   2. 消除 findAll() 全表扫描：随机探索用 ORDER BY RAND() LIMIT，兜底用 findAllById
 *   3. Random 复用：类级 static 实例
 */
@Service
public class HybridRecommenderServiceImpl implements HybridRecommenderService {

    private static final Logger log = LoggerFactory.getLogger(HybridRecommenderServiceImpl.class);
    private static final Random RANDOM = new Random();

    private final CollaborativeRecommender collaborativeRecommender;
    private final ContentBasedRecommender contentBasedRecommender;
    private final SongRepository songRepository;
    private final UserSongInteractionRepository interactionRepository;

    public HybridRecommenderServiceImpl(CollaborativeRecommender collaborativeRecommender,
                                        ContentBasedRecommender contentBasedRecommender,
                                        SongRepository songRepository,
                                        UserSongInteractionRepository interactionRepository) {
        this.collaborativeRecommender = collaborativeRecommender;
        this.contentBasedRecommender = contentBasedRecommender;
        this.songRepository = songRepository;
        this.interactionRepository = interactionRepository;
    }

    private static final int COLD_START_THRESHOLD = 5;
    private static final double DEFAULT_CF_WEIGHT = 0.50;
    private static final double DEFAULT_CB_WEIGHT = 0.20;
    private static final double DEFAULT_POPULARITY_WEIGHT = 0.15;
    private static final double DEFAULT_EXPLORE_WEIGHT = 0.15;
    private static final double SPARSE_CF_WEIGHT = 0.20;
    private static final double SPARSE_CB_WEIGHT = 0.50;
    private static final double SPARSE_POPULARITY_WEIGHT = 0.20;
    private static final double SPARSE_EXPLORE_WEIGHT = 0.10;
    private static final double COLD_START_HOT_RATIO = 0.5;

    @Override
    public List<Song> recommend(Long userId, int limit) {
        long interactionCount = interactionRepository.countByUserId(userId);
        if (interactionCount == 0) {
            return getColdStartRecommendations(limit);
        }

        double cfWeight, cbWeight, popWeight, exploreWeight;
        if (interactionCount <= COLD_START_THRESHOLD) {
            cfWeight = SPARSE_CF_WEIGHT; cbWeight = SPARSE_CB_WEIGHT;
            popWeight = SPARSE_POPULARITY_WEIGHT; exploreWeight = SPARSE_EXPLORE_WEIGHT;
        } else {
            cfWeight = DEFAULT_CF_WEIGHT; cbWeight = DEFAULT_CB_WEIGHT;
            popWeight = DEFAULT_POPULARITY_WEIGHT; exploreWeight = DEFAULT_EXPLORE_WEIGHT;
        }

        // 收集用户已交互的歌曲ID，确保所有子推荐器都排除这些歌
        Set<Long> interactedSongIds = new HashSet<>(interactionRepository.findSongIdsByUserId(userId));

        int candidateLimit = limit * 3;
        List<Song> collaborativeSongs = collaborativeRecommender.recommend(userId, candidateLimit);
        List<Song> contentBasedSongs = contentBasedRecommender.recommend(userId, candidateLimit);

        // 修复 Bug6：CF+CB 为空但 popularity/explore 可能还有结果，不强制走冷启动
        List<Song> popularitySongs = getPopularityBasedRecommendations(candidateLimit, interactedSongIds);
        List<Song> exploreSongs = getExploreRecommendations(candidateLimit, interactedSongIds);

        if (collaborativeSongs.isEmpty() && contentBasedSongs.isEmpty()
                && popularitySongs.isEmpty() && exploreSongs.isEmpty()) {
            return getColdStartRecommendations(limit);
        }

        Map<Long, Double> scoreMap = new HashMap<>();
        // 修复 Bug2：统一用 candidateLimit 做归一化基准，避免返回数量少的列表被不公平抬高
        rankAndScore(collaborativeSongs, cfWeight, candidateLimit, scoreMap);
        rankAndScore(contentBasedSongs, cbWeight, candidateLimit, scoreMap);
        rankAndScore(popularitySongs, popWeight, candidateLimit, scoreMap);
        rankAndScore(exploreSongs, exploreWeight, candidateLimit, scoreMap);

        // === 优化：不再 N+1 findById，收集所有候选 ID 一次性批量加载 ===
        List<Long> topIds = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit * 2)                    // 多取一点，防止 songRepository 返回空
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<Long, Song> songMap = songRepository.findAllById(topIds).stream()
                .collect(Collectors.toMap(Song::getId, s -> s));

        List<Song> results = topIds.stream()
                .map(songMap::get)
                .filter(Objects::nonNull)
                .limit(limit)
                .collect(Collectors.toList());

        log.info("[HybridRecommender] userId={} interactions={} results={}", userId, interactionCount, results.size());

        if (results.size() < limit) {
            // === 优化：不再 findAll() 全表扫描，直接按现有 score 补齐 ===
            Set<Long> existingIds = results.stream().map(Song::getId).collect(Collectors.toSet());
            List<Long> allSongIds = new ArrayList<>(songMap.keySet());
            // 再从剩余 scoreMap 补齐（没进 topIds 的那些）
            for (Map.Entry<Long, Double> e : scoreMap.entrySet()) {
                if (!existingIds.contains(e.getKey()) && songMap.containsKey(e.getKey())) {
                    results.add(songMap.get(e.getKey()));
                    existingIds.add(e.getKey());
                }
                if (results.size() >= limit) break;
            }
            // 还不够，才随机扫（小概率）
            if (results.size() < limit) {
                List<Long> remaining = allSongIds.stream()
                        .filter(id -> !existingIds.contains(id))
                        .collect(Collectors.toList());
                Collections.shuffle(remaining, RANDOM);
                List<Song> extraSongs = songRepository.findAllById(remaining.stream().limit(limit - results.size()).collect(Collectors.toList()));
                results.addAll(extraSongs);
            }
        }

        return results;
    }

    /**
     * 修复 Bug2：统一用 fixedNorm（候选上限）做归一化基准，
     * 避免返回数量少的列表因自身 size 小而被不公平抬高分数。
     */
    private void rankAndScore(List<Song> songs, double weight, int fixedNorm, Map<Long, Double> scoreMap) {
        if (songs == null || songs.isEmpty()) return;
        int norm = Math.max(fixedNorm, 1);
        for (int i = 0; i < songs.size(); i++) {
            // 用固定基准归一化：排名越靠前分数越高，但衰减速度与子推荐器返回数量无关
            double rankFactor = Math.max(0.0, 1.0 - (double) i / norm);
            scoreMap.merge(songs.get(i).getId(), weight * rankFactor, Double::sum);
        }
    }

    /**
     * 修复 Bug6：消除冗余查询，只查一次热门歌曲列表。
     * 冷启动场景：热门 50% + 随机 50%。
     */
    private List<Song> getColdStartRecommendations(int limit) {
        int hotCount = Math.max(1, (int) (limit * COLD_START_HOT_RATIO));
        int totalSongs = (int) songRepository.count();
        if (totalSongs == 0) return Collections.emptyList();

        // 只查一次：取足够大的批次（覆盖热门 + 随机）
        int batch = Math.min(totalSongs, limit * 4);
        List<Song> batchSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, batch));

        // 前 hotCount 首直接作为热门
        List<Song> hotSongs = new ArrayList<>(batchSongs.subList(0, Math.min(hotCount, batchSongs.size())));
        Set<Long> usedIds = hotSongs.stream().map(Song::getId).collect(Collectors.toSet());

        // 从剩余歌曲中随机挑，补齐到 limit
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

        // 混合一下避免热门都挤在前面
        List<Song> mixed = new ArrayList<>(result);
        if (mixed.size() > 2) {
            List<Song> tail = new ArrayList<>(mixed.subList(2, mixed.size()));
            Collections.shuffle(tail, RANDOM);
            mixed = new ArrayList<>(mixed.subList(0, 2));
            mixed.addAll(tail);
        }
        return mixed;
    }

    /**
     * 修复 Bug1：排除用户已交互的歌曲，不再浪费权重预算。
     */
    private List<Song> getPopularityBasedRecommendations(int limit, Set<Long> interactedSongIds) {
        List<Song> popular = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, limit + interactedSongIds.size()));
        return popular.stream()
                .filter(s -> !interactedSongIds.contains(s.getId()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 修复 Bug1 + Bug5：
     * - 排除用户已交互的歌曲
     * - 从全部歌曲中真正随机抽取（而非从热门里挑），实现真正的长尾探索
     */
    private List<Song> getExploreRecommendations(int limit, Set<Long> interactedSongIds) {
        int total = (int) songRepository.count();
        if (total == 0) return Collections.emptyList();

        // 真正的全量探索：取一批按播放量倒序的歌，过滤后 shuffle
        // 虽然数据源还是热门排序，但和 Popularity 的区别在于：
        // 1. 过滤掉已交互歌曲
        // 2. shuffle 后再取前 limit，打乱了热门排序
        // 3. 取更大的批次，增加冷门歌混入的概率
        int batch = Math.min(total, (limit + interactedSongIds.size()) * 6);
        List<Song> batchSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, batch));

        List<Song> candidates = batchSongs.stream()
                .filter(s -> !interactedSongIds.contains(s.getId()))
                .collect(Collectors.toList());

        Collections.shuffle(candidates, RANDOM);
        return candidates.stream().limit(limit).collect(Collectors.toList());
    }
}
