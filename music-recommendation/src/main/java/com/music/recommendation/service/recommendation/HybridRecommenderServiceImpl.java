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
 * 混合推荐引擎实现（三种算法融合）
 *
 * 推荐策略权重分配：
 * - 协同过滤推荐 (50%)：UserCF + ItemCF，核心算法
 * - 基于内容推荐 (20%)：按风格/歌手/专辑特征匹配用户画像
 * - 基于流行度推荐 (15%)：推荐全局热门歌曲
 * - 多样性探索 (15%)：引入随机性，增加推荐结果的多样性
 *
 * 冷启动策略：
 * - 新用户（无交互记录）：热门 50% + 随机 50% 打散
 * - 有少量交互（≤5 条）：内容推荐权重提升到 40%，协同过滤降低
 * - 活跃用户（>20 条交互）：协同过滤权重提升到 60%
 * - 新歌曲（无交互数据）：通过随机探索获得曝光机会
 */
@Service
public class HybridRecommenderServiceImpl implements HybridRecommenderService {

    private static final Logger log = LoggerFactory.getLogger(HybridRecommenderServiceImpl.class);

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

    /** 冷启动阈值 */
    private static final int COLD_START_THRESHOLD = 5;

    /** 默认权重（活跃用户） */
    private static final double DEFAULT_CF_WEIGHT = 0.50;
    private static final double DEFAULT_CB_WEIGHT = 0.20;
    private static final double DEFAULT_POPULARITY_WEIGHT = 0.15;
    private static final double DEFAULT_EXPLORE_WEIGHT = 0.15;

    /** 冷启动早期权重 */
    private static final double SPARSE_CF_WEIGHT = 0.20;
    private static final double SPARSE_CB_WEIGHT = 0.50;
    private static final double SPARSE_POPULARITY_WEIGHT = 0.20;
    private static final double SPARSE_EXPLORE_WEIGHT = 0.10;

    /** 冷启动时热门占比 */
    private static final double COLD_START_HOT_RATIO = 0.5;

    /** 为指定用户生成混合推荐 */
    @Override
    public List<Song> recommend(Long userId, int limit) {
        long interactionCount = interactionRepository.countByUserId(userId);
        if (interactionCount == 0) {
            return getColdStartRecommendations(limit);
        }

        double cfWeight, cbWeight, popWeight, exploreWeight;
        if (interactionCount <= COLD_START_THRESHOLD) {
            cfWeight = SPARSE_CF_WEIGHT;
            cbWeight = SPARSE_CB_WEIGHT;
            popWeight = SPARSE_POPULARITY_WEIGHT;
            exploreWeight = SPARSE_EXPLORE_WEIGHT;
        } else {
            cfWeight = DEFAULT_CF_WEIGHT;
            cbWeight = DEFAULT_CB_WEIGHT;
            popWeight = DEFAULT_POPULARITY_WEIGHT;
            exploreWeight = DEFAULT_EXPLORE_WEIGHT;
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

        for (int i = 0; i < collaborativeSongs.size(); i++) {
            double score = cfWeight * (1.0 - (double) i / collaborativeSongs.size());
            scoreMap.merge(collaborativeSongs.get(i).getId(), score, Double::sum);
        }
        for (int i = 0; i < contentBasedSongs.size(); i++) {
            double score = cbWeight * (1.0 - (double) i / contentBasedSongs.size());
            scoreMap.merge(contentBasedSongs.get(i).getId(), score, Double::sum);
        }
        for (int i = 0; i < popularitySongs.size(); i++) {
            double score = popWeight * (1.0 - (double) i / popularitySongs.size());
            scoreMap.merge(popularitySongs.get(i).getId(), score, Double::sum);
        }
        for (int i = 0; i < exploreSongs.size(); i++) {
            double score = exploreWeight * (1.0 - (double) i / exploreSongs.size());
            scoreMap.merge(exploreSongs.get(i).getId(), score, Double::sum);
        }

        List<Song> results = scoreMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> songRepository.findById(entry.getKey()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.info("[HybridRecommender] userId={} interactions={} cfCount={} cbCount={} popCount={} exploreCount={} results={}",
                userId, interactionCount,
                collaborativeSongs.size(), contentBasedSongs.size(),
                popularitySongs.size(), exploreSongs.size(),
                results.size());

        if (results.size() < limit) {
            Set<Long> existingIds = results.stream().map(Song::getId).collect(Collectors.toSet());
            List<Song> allSongs = songRepository.findAll();
            Collections.shuffle(allSongs, new Random());
            for (Song song : allSongs) {
                if (!existingIds.contains(song.getId())) {
                    results.add(song);
                    existingIds.add(song.getId());
                }
                if (results.size() >= limit) break;
            }
        }

        return results;
    }

    /** 冷启动推荐 */
    private List<Song> getColdStartRecommendations(int limit) {
        int hotCount = Math.max(1, (int) (limit * COLD_START_HOT_RATIO));
        List<Song> hotSongs = songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, hotCount + 3));
        List<Song> allSongs = songRepository.findAll();
        Collections.shuffle(allSongs, new Random());

        List<Song> result = new ArrayList<>(hotSongs.subList(0, Math.min(hotCount, hotSongs.size())));

        Set<Long> usedIds = result.stream().map(Song::getId).collect(Collectors.toSet());
        for (Song song : allSongs) {
            if (!usedIds.contains(song.getId())) {
                result.add(song);
                usedIds.add(song.getId());
            }
            if (result.size() >= limit) break;
        }

        List<Song> finalResult = new ArrayList<>(result);
        List<Song> front = finalResult.subList(0, Math.min(2, finalResult.size()));
        List<Song> tail = new ArrayList<>(finalResult.subList(Math.min(2, finalResult.size()), finalResult.size()));
        Collections.shuffle(tail, new Random());

        List<Song> shuffled = new ArrayList<>(front);
        shuffled.addAll(tail);
        return shuffled;
    }

    /** 基于流行度推荐 */
    private List<Song> getPopularityBasedRecommendations(int limit) {
        return songRepository.findAllByOrderByPlayCountDesc(PageRequest.of(0, limit));
    }

    /** 随机探索推荐 */
    private List<Song> getExploreRecommendations(int limit) {
        List<Song> allSongs = songRepository.findAll();
        if (allSongs.isEmpty()) return Collections.emptyList();
        Collections.shuffle(allSongs, new Random());
        return allSongs.subList(0, Math.min(limit, allSongs.size()));
    }
}
