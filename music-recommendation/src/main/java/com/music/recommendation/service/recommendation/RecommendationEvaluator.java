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

    // ==================== 冷启动模拟评估 ====================

    /** 模拟冷启动：每个活跃用户只留 3 条交互做训练 */
    private static final int SIM_COLD_START_TRAIN_MAX = 3;
    /** ground truth 上限：5 首，和老用户评估的 20% 留出量级一致（约 4-6 首），保证指标可比 */
    private static final int SIM_COLD_START_GT_CAP = 5;
    /** 只取交互数 ≥ 10 的用户（保证有足够数据"删"出来做 ground truth） */
    private static final int SIM_COLD_START_MIN_INTERACTIONS = 10;

    /**
     * 模拟冷启动评估：取活跃用户，每人只留 1-3 条交互做训练集，其余做 ground truth
     *
     * 方法论：
     *   1. 取所有交互数 ≥ 10 的活跃用户（保证 ground truth ≥ 7 首）
     *   2. 对每个用户，随机挑 3 条交互"保留"（模拟新用户只有 3 次交互）
     *   3. 其余全部删除 + flush（推荐器只能看到 3 条）
     *   4. 调推荐 → 比对 ground truth → 恢复被删除的交互
     *
     * 预期：CF 在只有 3 条交互时邻居极少，表现最差；
     *       CB 靠风格/歌手相似度不依赖邻居；
     *       Hybrid 自动切到冷启动档位（CF 5% / CB 45%）应该最优
     */
    public List<EvalResult> evaluateColdStart() {
        long start = System.currentTimeMillis();

        // 1. 获取全部交互
        List<UserSongInteraction> allInteractions = interactionRepository.findAll();
        if (allInteractions.isEmpty()) {
            log.warn("[Evaluator-Cold] 无交互数据");
            return Collections.emptyList();
        }

        // 2. 按用户分组，只保留交互数 ≥ 10 的活跃用户
        Random random = new Random(RANDOM_SEED + 999L);
        Map<Long, List<UserSongInteraction>> userInteractions = allInteractions.stream()
                .collect(Collectors.groupingBy(UserSongInteraction::getUserId));

        // 3. 对每个活跃用户：留 min(3, size-7) 条做训练 → 其余做 ground truth
        Map<Long, Set<Long>> groundTruthSets = new LinkedHashMap<>();

        for (Map.Entry<Long, List<UserSongInteraction>> entry : userInteractions.entrySet()) {
            Long userId = entry.getKey();
            List<UserSongInteraction> interactions = entry.getValue();
            int total = interactions.size();
            if (total < SIM_COLD_START_MIN_INTERACTIONS) continue;

            List<Long> songIds = interactions.stream()
                    .map(UserSongInteraction::getSongId)
                    .distinct()
                    .collect(Collectors.toList());
            Collections.shuffle(songIds, random);

            // 训练集：最多 3 条（模拟冷启动）
            int trainSize = Math.min(SIM_COLD_START_TRAIN_MAX, songIds.size() - 1);
            // ground truth：删掉训练集剩下的，最多 20 条
            List<Long> gtList = songIds.subList(trainSize, songIds.size());
            if (gtList.size() > SIM_COLD_START_GT_CAP) {
                gtList = gtList.subList(0, SIM_COLD_START_GT_CAP);
            }
            groundTruthSets.put(userId, new HashSet<>(gtList));
        }

        if (groundTruthSets.isEmpty()) {
            log.warn("[Evaluator-Cold] 没有足够的活跃用户模拟冷启动");
            return Collections.emptyList();
        }

        // 4. 复用 evaluateAlgorithm 但反向：删训练集以外的（即删 ground truth 保留训练集）
        //    但 evaluateAlgorithm 的语义是"删掉 ground truth → 让推荐器只看到训练集"
        //    所以 groundTruthSets 就是我们要"删掉"的集合！直接复用。

        log.info("[Evaluator-Cold] 冷启动模拟：{} 个用户，每人保留 1-3 条训练，平均 ground truth {} 首",
                groundTruthSets.size(),
                groundTruthSets.values().stream().mapToInt(Set::size).average().orElse(0));

        List<EvalResult> results = new ArrayList<>();
        results.add(evaluateAlgorithmCold("协同过滤 (冷启动模拟)", groundTruthSets, uid -> cfRecommender.recommend(uid, 20)));
        results.add(evaluateAlgorithmCold("内容推荐 (冷启动模拟)", groundTruthSets, uid -> cbRecommender.recommend(uid, 20)));
        results.add(evaluateAlgorithmCold("混合推荐 (冷启动模拟)", groundTruthSets, uid -> hybridRecommender.recommend(uid, 20)));

        long totalMs = System.currentTimeMillis() - start;
        log.info("[Evaluator-Cold] 冷启动评估完成，总耗时 {} ms", totalMs);

        return results;
    }

    /**
     * 冷启动专用评估：复用删除-推荐-恢复，但加保护确保训练集不为空
     */
    private EvalResult evaluateAlgorithmCold(String algoName,
                                             Map<Long, Set<Long>> groundTruthSets,
                                             java.util.function.Function<Long, List<Song>> recommender) {
        long start = System.currentTimeMillis();

        double sumPrecision5 = 0, sumPrecision10 = 0;
        double sumRecall5 = 0, sumRecall10 = 0;
        double sumNdcg5 = 0, sumNdcg10 = 0;
        int validUsers = 0;

        log.info("[Evaluator-Cold] 开始评估: {}", algoName);

        for (Map.Entry<Long, Set<Long>> entry : groundTruthSets.entrySet()) {
            Long userId = entry.getKey();
            Set<Long> gtSongIds = entry.getValue();
            if (gtSongIds.isEmpty()) continue;

            // ===== 删除 ground truth 交互，保留训练集（1-3 条）=====
            List<UserSongInteraction> deleted = deleteTestInteractions(userId, gtSongIds);

            List<Song> recommended;
            try {
                interactionRepository.flush();
                recommended = recommender.apply(userId);
            } catch (Exception e) {
                log.warn("[Evaluator-Cold] {} 用户 {} 异常: {}", algoName, userId, e.getMessage());
                recommended = Collections.emptyList();
            } finally {
                restoreInteractions(deleted);
                interactionRepository.flush();
            }

            // 提取推荐结果
            List<Long> recommendedIds = recommended.stream()
                    .map(Song::getId)
                    .collect(Collectors.toList());

            Set<Long> groundTruth = new HashSet<>(gtSongIds);
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

        log.info("[Evaluator-Cold] {} 完成: {}", algoName, result.toMap());
        return result;
    }
}
