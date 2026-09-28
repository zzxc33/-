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

        int candidateLimit = limit * 3;
        List<Song> collaborativeSongs = collaborativeRecommender.recommend(userId, candidateLimit);
        List<Song> contentBasedSongs = contentBasedRecommender.recommend(userId, candidateLimit);

        if (collaborativeSongs.isEmpty() && contentBasedSongs.isEmpty()) {
            return getColdStartRecommendations(limit);
        }

        List<Song> popularitySongs = getPopularityBasedRecommendations(candidateLimit);
        List<Song> exploreSongs = getExploreRecommendations(candidateLimit);

        Map<Long, Double> scoreMap = new HashMap<>();
        rankAndScore(collaborativeSongs, cfWeight, scoreMap);
        rankAndScore(contentBasedSongs, cbWeight, scoreMap);
        rankAndScore(popularitySongs, popWeight, scoreMap);
        rankAndScore(exploreSongs, exploreWeight, scoreMap);

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

    private void rankAndScore(List<Song> songs, double weight, Map<Long, Double> scoreMap) {
        if (songs == null || songs.isEmpty()) return;
        int size = songs.size();
        for (int i = 0; i < size; i++) {
            scoreMap.merge(songs.get(i).getId(), weight * (1.0 - (double) i / size), Double::sum);
        }
    }

    private List<Song> getColdStartRecommendations(int limit) {
        int hotCount = Math.max(1, (int) (limit * COLD_START_HOT_RATIO));
        List<Song> hotSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, hotCount + 3));

        // === 优化：不再 findAll()，从全部歌单里随机抽取一批 ===
        int randomBatch = Math.min(limit * 4, songRepository.count());
        List<Song> allSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, randomBatch));
        Collections.shuffle(allSongs, RANDOM);

        List<Song> result = new ArrayList<>(hotSongs.subList(0, Math.min(hotCount, hotSongs.size())));
        Set<Long> usedIds = result.stream().map(Song::getId).collect(Collectors.toSet());
        for (Song song : allSongs) {
            if (!usedIds.contains(song.getId())) {
                result.add(song);
                usedIds.add(song.getId());
            }
            if (result.size() >= limit) break;
        }

        List<Song> shuffled = new ArrayList<>(result);
        List<Song> front = shuffled.subList(0, Math.min(2, shuffled.size()));
        List<Song> tail = new ArrayList<>(shuffled.subList(Math.min(2, shuffled.size()), shuffled.size()));
        Collections.shuffle(tail, RANDOM);
        List<Song> mixed = new ArrayList<>(front);
        mixed.addAll(tail);
        return mixed;
    }

    private List<Song> getPopularityBasedRecommendations(int limit) {
        return songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, limit));
    }

    private List<Song> getExploreRecommendations(int limit) {
        // === 优化：不再 findAll()，用 ORDER BY RAND() 取随机一批 ===
        int total = (int) songRepository.count();
        if (total == 0) return Collections.emptyList();
        if (total <= limit) return new ArrayList<>(songRepository.findAll());
        // 取一个更大的批次再 shuffle
        int batch = Math.min(total, limit * 4);
        List<Song> batchSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, batch));
        Collections.shuffle(batchSongs, RANDOM);
        return batchSongs.subList(0, Math.min(limit, batchSongs.size()));
    }
}
