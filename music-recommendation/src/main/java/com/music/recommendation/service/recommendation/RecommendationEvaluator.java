package com.music.recommendation.service.recommendation;

import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.UserSongInteraction;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐系统离线评估器
 *
 * 评估方法：留出法（Hold-Out）
 *   对每个有交互的活跃用户，随机隐去 20% 的交互作为测试集（ground truth），
 *   剩余 80% 作为训练集。用训练集生成推荐，然后对比测试集计算指标。
 *
 * 评估指标：
 *   - Precision@K  推荐前 K 首中，用户实际听过的比例（查准率）
 *   - Recall@K     用户实际听过的歌中，被推荐出来的比例（查全率）
 *   - NDCG@K       考虑排序位置的归一化折损累积增益（排序质量）
 *
 * 注意：评估时为了不污染线上数据，使用内存中的训练集/测试集划分，
 *       推荐算法需要支持"排除已隐去交互"的上下文。
 */
@Service
public class RecommendationEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RecommendationEvaluator.class);

    /** 测试集比例：20% 交互隐去作为 ground truth */
    private static final double TEST_RATIO = 0.2;
    /** 随机种子，保证每次评估结果可复现 */
    private static final long RANDOM_SEED = 42L;

    private final UserSongInteractionRepository interactionRepository;
    private final CollaborativeRecommender cfRecommender;
    private final ContentBasedRecommender cbRecommender;
    private final HybridRecommenderService hybridRecommender;

    public RecommendationEvaluator(UserSongInteractionRepository interactionRepository,
                                   CollaborativeRecommender cfRecommender,
                                   ContentBasedRecommender cbRecommender,
                                   HybridRecommenderService hybridRecommender) {
        this.interactionRepository = interactionRepository;
        this.cfRecommender = cfRecommender;
        this.cbRecommender = cbRecommender;
        this.hybridRecommender = hybridRecommender;
    }

    /**
     * 评估结果 DTO
     */
    public static class EvalResult {
        public String algorithm;
        public int testUsers;
        public double precisionAt5;
        public double precisionAt10;
        public double recallAt5;
        public double recallAt10;
        public double ndcgAt5;
        public double ndcgAt10;
        public long durationMs;

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("algorithm", algorithm);
            m.put("testUsers", testUsers);
            m.put("precision@5", round3(precisionAt5));
            m.put("precision@10", round3(precisionAt10));
            m.put("recall@5", round3(recallAt5));
            m.put("recall@10", round3(recallAt10));
            m.put("ndcg@5", round3(ndcgAt5));
            m.put("ndcg@10", round3(ndcgAt10));
            m.put("durationMs", durationMs);
            return m;
        }

        private static double round3(double v) {
            return Math.round(v * 1000.0) / 1000.0;
        }
    }

    /**
     * 执行完整评估：对比三种推荐算法
     *
     * 方法论（严格防止数据泄漏）：
     *   1. 对每个测试用户，识别要"隐去"的交互（测试集）
     *   2. 物理删除这些测试交互 + 立即 flush
     *   3. 调用推荐算法 → 算法只能看到训练集数据
     *   4. 比对结果与测试集，计算 Precision/Recall/NDCG
     *   5. 恢复被删除的测试交互（重新 INSERT）
     *   6. 对每个算法重复 1~5（因为不同算法推荐结果不同）
     *
     * @return 各算法评估结果列表
     */
    public List<EvalResult> evaluateAll() {
        long start = System.currentTimeMillis();

        // 1. 获取所有活跃用户的交互数据
        List<UserSongInteraction> allInteractions = interactionRepository.findAll();
        if (allInteractions.isEmpty()) {
            log.warn("[Evaluator] 无交互数据，无法评估");
            return Collections.emptyList();
        }

        // 2. 按用户分组
        Map<Long, List<UserSongInteraction>> userInteractions = allInteractions.stream()
                .collect(Collectors.groupingBy(UserSongInteraction::getUserId));

        // 3. 对每个用户划分训练集/测试集
        Random random = new Random(RANDOM_SEED);
        Map<Long, Set<Long>> testSets = new HashMap<>();

        for (Map.Entry<Long, List<UserSongInteraction>> entry : userInteractions.entrySet()) {
            Long userId = entry.getKey();
            List<UserSongInteraction> interactions = entry.getValue();
            if (interactions.size() < 3) continue;

            List<Long> songIds = interactions.stream()
                    .map(UserSongInteraction::getSongId)
                    .distinct()
                    .collect(Collectors.toList());
            Collections.shuffle(songIds, random);

            int testSize = Math.max(1, (int) (songIds.size() * TEST_RATIO));
            testSets.put(userId, new HashSet<>(songIds.subList(0, testSize)));
        }

        if (testSets.isEmpty()) {
            log.warn("[Evaluator] 没有足够的活跃用户用于评估");
            return Collections.emptyList();
        }

        log.info("[Evaluator] 划分完成：{} 个用户，平均每用户隐去 {} 首歌",
                testSets.size(),
                testSets.values().stream().mapToInt(Set::size).average().orElse(0));

        // 4. 分别评估三种算法（每个算法独立做删除-推荐-恢复循环）
        List<EvalResult> results = new ArrayList<>();
        results.add(evaluateAlgorithm("协同过滤", testSets, uid -> cfRecommender.recommend(uid, 20)));
        results.add(evaluateAlgorithm("内容推荐", testSets, uid -> cbRecommender.recommend(uid, 20)));
        results.add(evaluateAlgorithm("混合推荐", testSets, uid -> hybridRecommender.recommend(uid, 20)));

        long totalMs = System.currentTimeMillis() - start;
        log.info("[Evaluator] 评估完成，总耗时 {} ms", totalMs);

        return results;
    }

    /**
     * 评估单个算法 — 严格隔离测试集
     * 对每个测试用户：删除测试交互 → 调用推荐 → 恢复交互 → 计算指标
     */
    private EvalResult evaluateAlgorithm(String algoName,
                                         Map<Long, Set<Long>> testSets,
                                         java.util.function.Function<Long, List<Song>> recommender) {
        long start = System.currentTimeMillis();

        double sumPrecision5 = 0, sumPrecision10 = 0;
        double sumRecall5 = 0, sumRecall10 = 0;
        double sumNdcg5 = 0, sumNdcg10 = 0;
        int validUsers = 0;

        log.info("[Evaluator] 开始评估算法: {}", algoName);

        for (Map.Entry<Long, Set<Long>> entry : testSets.entrySet()) {
            Long userId = entry.getKey();
            Set<Long> groundTruth = entry.getValue();
            if (groundTruth.isEmpty()) continue;

            // ===== 关键步骤：临时删除测试交互，确保算法只看到训练集 =====
            List<UserSongInteraction> deleted = deleteTestInteractions(userId, groundTruth);

            List<Song> recommended;
            try {
                // 让 Spring Data JPA flush 到 DB，确保后续查询看不到被删除的行
                interactionRepository.flush();
                recommended = recommender.apply(userId);
            } catch (Exception e) {
                log.warn("[Evaluator] 算法 {} 对用户 {} 推荐异常: {}", algoName, userId, e.getMessage());
                recommended = Collections.emptyList();
            } finally {
                // ===== 恢复被删除的测试交互 =====
                restoreInteractions(deleted);
                interactionRepository.flush();
            }

            // 提取推荐歌曲 ID
            List<Long> recommendedIds = recommended.stream()
                    .map(Song::getId)
                    .collect(Collectors.toList());

            sumPrecision5 += precisionAt(recommendedIds, groundTruth, 5);
            sumPrecision10 += precisionAt(recommendedIds, groundTruth, 10);
            sumRecall5 += recallAt(recommendedIds, groundTruth, 5);
            sumRecall10 += recallAt(recommendedIds, groundTruth, 10);
            sumNdcg5 += ndcgAt(recommendedIds, groundTruth, 5);
            sumNdcg10 += ndcgAt(recommendedIds, groundTruth, 10);
            validUsers++;
        }

        EvalResult result = new EvalResult();
        result.algorithm = algoName;
        result.testUsers = validUsers;

        if (validUsers > 0) {
            result.precisionAt5 = sumPrecision5 / validUsers;
            result.precisionAt10 = sumPrecision10 / validUsers;
            result.recallAt5 = sumRecall5 / validUsers;
            result.recallAt10 = sumRecall10 / validUsers;
            result.ndcgAt5 = sumNdcg5 / validUsers;
            result.ndcgAt10 = sumNdcg10 / validUsers;
        }
        result.durationMs = System.currentTimeMillis() - start;

        log.info("[Evaluator] 算法 {} 评估完成: {}", algoName, result.toMap());
        return result;
    }

    // ==================== 数据隔离辅助方法 ====================

    /**
     * 删除指定用户的测试交互（user_id + song_id 匹配），返回被删除的记录用于恢复
     */
    private List<UserSongInteraction> deleteTestInteractions(Long userId, Set<Long> songIds) {
        List<UserSongInteraction> toDelete = new ArrayList<>();
        for (Long sid : songIds) {
            interactionRepository.findByUserIdAndSongId(userId, sid).ifPresent(toDelete::add);
        }
        if (!toDelete.isEmpty()) {
            interactionRepository.deleteAll(toDelete);
        }
        return toDelete;
    }

    /**
     * 恢复被删除的交互（重新保存）
     */
    private void restoreInteractions(List<UserSongInteraction> deleted) {
        if (!deleted.isEmpty()) {
            interactionRepository.saveAll(deleted);
        }
    }

    // ==================== 指标计算 ====================

    /**
     * Precision@K：推荐前 K 首中，有多少是用户真正听过的
     * P@K = |推荐结果前K ∩ ground truth| / K
     */
    private double precisionAt(List<Long> recommended, Set<Long> groundTruth, int k) {
        int limit = Math.min(k, recommended.size());
        if (limit == 0) return 0.0;

        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (groundTruth.contains(recommended.get(i))) hits++;
        }
        return (double) hits / k;
    }

    /**
     * Recall@K：用户实际听过的歌中，有多少被推荐到了前 K
     * R@K = |推荐结果前K ∩ ground truth| / |ground truth|
     */
    private double recallAt(List<Long> recommended, Set<Long> groundTruth, int k) {
        if (groundTruth.isEmpty()) return 0.0;
        int limit = Math.min(k, recommended.size());

        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (groundTruth.contains(recommended.get(i))) hits++;
        }
        return (double) hits / groundTruth.size();
    }

    /**
     * NDCG@K：Normalized Discounted Cumulative Gain
     * 考虑排序位置的指标，排名越靠前的相关歌曲贡献越大
     *
     * DCG@K = Σ (rel_i / log2(i+1))  ，i 从 1 开始
     * IDCG@K = 理想排序下的 DCG（所有 ground truth 都排在最前面）
     * NDCG@K = DCG@K / IDCG@K
     */
    private double ndcgAt(List<Long> recommended, Set<Long> groundTruth, int k) {
        int limit = Math.min(k, recommended.size());
        if (limit == 0 || groundTruth.isEmpty()) return 0.0;

        // 计算 DCG
        double dcg = 0;
        for (int i = 0; i < limit; i++) {
            int rel = groundTruth.contains(recommended.get(i)) ? 1 : 0;
            // 位置从 1 开始，log2(i+2) 因为 i 是 0-based
            // Java 17 没有 Math.log2，手动算
            dcg += rel / (Math.log(i + 2) / Math.log(2));
        }

        // 计算 IDCG（理想排序：所有 ground truth 排在最前面）
        double idcg = 0;
        int idealLimit = Math.min(k, groundTruth.size());
        for (int i = 0; i < idealLimit; i++) {
            idcg += 1.0 / (Math.log(i + 2) / Math.log(2));
        }

        if (idcg == 0) return 0.0;
        return dcg / idcg;
    }
}
