package com.music.recommendation.service.recommendation;

import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 推荐评估指标计算测试
 *
 * 验证 Precision@K、Recall@K、NDCG@K 的计算逻辑正确性
 */
@ExtendWith(MockitoExtension.class)
class RecommendationEvaluatorMetricTest {

    @Mock UserSongInteractionRepository interactionRepository;
    @Mock SongRepository songRepository;

    private RecommendationEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RecommendationEvaluator(interactionRepository, null, null, null, null);
    }

    // ==================== Precision@K ====================

    @Test
    @DisplayName("Precision@5 - 全部命中 → 1.0")
    void precision_allHit() {
        List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L, 4L, 5L);
        // 前 5 全部命中 → 5/5 = 1.0
        double p = invokePrecisionAt(recommended, groundTruth, 5);
        assertEquals(1.0, p, 0.001);
    }

    @Test
    @DisplayName("Precision@5 - 部分命中 → 命中数/K")
    void precision_partialHit() {
        List<Long> recommended = Arrays.asList(1L, 99L, 2L, 98L, 97L, 3L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);
        // 前 5 个中命中 1,2 → 2/5 = 0.4
        double p = invokePrecisionAt(recommended, groundTruth, 5);
        assertEquals(0.4, p, 0.001);
    }

    @Test
    @DisplayName("Precision@K - 推荐不足 K 条时仍用 K 做分母")
    void precision_fewResults() {
        List<Long> recommended = Arrays.asList(1L, 2L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);
        // 虽然只返回 2 条，但 Precision@5 = 2/5 = 0.4
        double p = invokePrecisionAt(recommended, groundTruth, 5);
        assertEquals(0.4, p, 0.001);
    }

    @Test
    @DisplayName("Precision@K - 空推荐 → 0")
    void precision_empty() {
        List<Long> recommended = Collections.emptyList();
        Set<Long> groundTruth = Set.of(1L, 2L);
        assertEquals(0.0, invokePrecisionAt(recommended, groundTruth, 5));
    }

    // ==================== Recall@K ====================

    @Test
    @DisplayName("Recall@5 - 全部召回 → 1.0")
    void recall_allFound() {
        List<Long> recommended = Arrays.asList(1L, 2L, 3L, 4L, 5L, 6L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);
        // 3 个 ground truth 都在推荐前 5 → 3/3 = 1.0
        double r = invokeRecallAt(recommended, groundTruth, 5);
        assertEquals(1.0, r, 0.001);
    }

    @Test
    @DisplayName("Recall@5 - 部分召回")
    void recall_partial() {
        List<Long> recommended = Arrays.asList(99L, 98L, 1L, 97L, 2L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);
        // ground truth 有 3 个，找到 2 个 → 2/3 ≈ 0.667
        double r = invokeRecallAt(recommended, groundTruth, 5);
        assertEquals(2.0 / 3, r, 0.001);
    }

    @Test
    @DisplayName("Recall@K - ground truth 为空 → 0")
    void recall_emptyGroundTruth() {
        List<Long> recommended = Arrays.asList(1L, 2L);
        Set<Long> groundTruth = Collections.emptySet();
        assertEquals(0.0, invokeRecallAt(recommended, groundTruth, 5));
    }

    // ==================== NDCG@K ====================

    @Test
    @DisplayName("NDCG@5 - 理想排序（所有相关排在最前）→ 1.0")
    void ndcg_ideal() {
        List<Long> recommended = Arrays.asList(1L, 2L, 3L, 99L, 98L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);
        // DCG = 1/log2(2) + 1/log2(3) + 1/log2(4)
        // IDCG = 同样（因为前 3 就是所有相关）
        // NDCG = DCG/IDCG = 1.0
        double n = invokeNdcgAt(recommended, groundTruth, 5);
        assertEquals(1.0, n, 0.001);
    }

    @Test
    @DisplayName("NDCG@5 - 最差排序（相关排在最后）→ 低于理想")
    void ndcg_poorRanking() {
        List<Long> recommended = Arrays.asList(99L, 98L, 97L, 1L, 2L);
        Set<Long> groundTruth = Set.of(1L, 2L, 3L);
        // DCG = 0 + 0 + 0 + 1/log2(5) + 1/log2(6)
        // IDCG = 1/log2(2) + 1/log2(3) + 1/log2(4)
        // NDCG 应该显著低于 1.0
        double n = invokeNdcgAt(recommended, groundTruth, 5);
        assertTrue(n < 0.5, "差排序的 NDCG 应显著低于理想值: " + n);
    }

    @Test
    @DisplayName("NDCG@K - 无相关 → 0")
    void ndcg_noRelevant() {
        List<Long> recommended = Arrays.asList(99L, 98L, 97L);
        Set<Long> groundTruth = Set.of(1L, 2L);
        assertEquals(0.0, invokeNdcgAt(recommended, groundTruth, 5));
    }

    @Test
    @DisplayName("NDCG@K - 空推荐 → 0")
    void ndcg_emptyList() {
        List<Long> recommended = Collections.emptyList();
        Set<Long> groundTruth = Set.of(1L);
        assertEquals(0.0, invokeNdcgAt(recommended, groundTruth, 5));
    }

    // ==================== 通过反射调用 private 方法 ====================
    // 因为 evaluate 方法是 public 但依赖 DB mock，这里直接反射调用指标方法

    private double invokePrecisionAt(List<Long> recommended, Set<Long> groundTruth, int k) {
        return invokePrivate("precisionAt", new Class[]{List.class, Set.class, int.class},
                recommended, groundTruth, k);
    }

    private double invokeRecallAt(List<Long> recommended, Set<Long> groundTruth, int k) {
        return invokePrivate("recallAt", new Class[]{List.class, Set.class, int.class},
                recommended, groundTruth, k);
    }

    private double invokeNdcgAt(List<Long> recommended, Set<Long> groundTruth, int k) {
        return invokePrivate("ndcgAt", new Class[]{List.class, Set.class, int.class},
                recommended, groundTruth, k);
    }

    private double invokePrivate(String name, Class<?>[] paramTypes, Object... args) {
        try {
            var method = RecommendationEvaluator.class.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return (double) method.invoke(evaluator, args);
        } catch (Exception e) {
            throw new RuntimeException("反射调用 " + name + " 失败", e);
        }
    }
}
