package com.music.recommendation.service.recommendation;

import com.music.recommendation.entity.UserSongInteraction;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 协同过滤算法单元测试
 *
 * 覆盖：
 *   1. Jaccard 相似度 — 空集、相同集、无交集、部分交集
 *   2. 皮尔逊相关系数 — 完全正相关、完全负相关、零相关、数据不足退化为 Jaccard
 *   3. computeUserPreference — 基础分、播放次数加分、点赞加分、时效性加分、5 分封顶
 */
@ExtendWith(MockitoExtension.class)
class CollaborativeRecommenderTest {

    @Mock UserSongInteractionRepository interactionRepository;
    @Mock SongRepository songRepository;

    private CollaborativeRecommender recommender;

    @BeforeEach
    void setUp() {
        recommender = new CollaborativeRecommender(interactionRepository, songRepository);
    }

    // ==================== Jaccard 相似度 ====================

    @Test
    @DisplayName("Jaccard - 两个空集 → 0")
    void jaccard_emptySets() {
        assertEquals(0.0, recommender.computeJaccardSimilarity(new HashSet<>(), new HashSet<>()));
    }

    @Test
    @DisplayName("Jaccard - 一个空集 → 0")
    void jaccard_oneEmpty() {
        assertEquals(0.0, recommender.computeJaccardSimilarity(Set.of(1L, 2L), new HashSet<>()));
    }

    @Test
    @DisplayName("Jaccard - 完全相同 → 1.0")
    void jaccard_identical() {
        assertEquals(1.0, recommender.computeJaccardSimilarity(
                Set.of(1L, 2L, 3L), Set.of(1L, 2L, 3L)));
    }

    @Test
    @DisplayName("Jaccard - 完全无交集 → 0")
    void jaccard_noIntersection() {
        assertEquals(0.0, recommender.computeJaccardSimilarity(
                Set.of(1L, 2L), Set.of(3L, 4L)));
    }

    @Test
    @DisplayName("Jaccard - 部分交集 → |∩|/|∪|")
    void jaccard_partial() {
        // {1,2,3} ∩ {2,3,4} = {2,3} size=2, ∪ size=4 → 0.5
        assertEquals(0.5, recommender.computeJaccardSimilarity(
                Set.of(1L, 2L, 3L), Set.of(2L, 3L, 4L)), 0.001);
    }

    @Test
    @DisplayName("Jaccard - 完全包含 → 子集/并集比例")
    void jaccard_contained() {
        // {1,2} ∩ {1,2,3} = size 2, ∪ size 3 → 2/3
        assertEquals(2.0 / 3, recommender.computeJaccardSimilarity(
                Set.of(1L, 2L), Set.of(1L, 2L, 3L)), 0.001);
    }

    // ==================== Pearson 相关系数 ====================

    @Test
    @DisplayName("Pearson - 完全正相关（共同歌曲评分模式相同）")
    void pearson_perfectPositive() {
        // A: song1=5, song2=4, song3=3
        // B: song1=5, song2=4, song3=3  → 完全正相关
        List<UserSongInteraction> userA = buildInteractions(Map.of(1L, 5.0, 2L, 4.0, 3L, 3.0));
        List<UserSongInteraction> userB = buildInteractions(Map.of(1L, 5.0, 2L, 4.0, 3L, 3.0));

        double sim = recommender.computePearsonSimilarity(userA, userB);
        // 3 首共同歌，jaccardWeight = min(1.0, 3/10) = 0.3
        // pearson ≈ 1.0, jaccard = 3/3 = 1.0 → 1.0*0.3 + 1.0*0.7 = 1.0
        assertTrue(sim > 0.9, "应该接近 1.0, 实际: " + sim);
    }

    @Test
    @DisplayName("Pearson - 完全负相关")
    void pearson_perfectNegative() {
        // A: song1=5, song2=4, song3=3
        // B: song1=3, song2=4, song3=5  → 反向
        List<UserSongInteraction> userA = buildInteractions(Map.of(1L, 5.0, 2L, 4.0, 3L, 3.0));
        List<UserSongInteraction> userB = buildInteractions(Map.of(1L, 3.0, 2L, 4.0, 3L, 5.0));

        double sim = recommender.computePearsonSimilarity(userA, userB);
        // pearson ≈ -1，但 jaccard=1.0 权重 0.3 → -0.3 + 0.7 = 0.4
        assertTrue(sim > 0, "Jaccard 占主导，应为正值: " + sim);
    }

    @Test
    @DisplayName("Pearson - 仅 1 首共同歌 → 退化为 Jaccard")
    void pearson_fallbackToJaccard() {
        // 只有 1 首共同歌，皮尔逊无法计算，退化为 Jaccard
        List<UserSongInteraction> userA = buildInteractions(Map.of(1L, 4.0, 2L, 3.0, 3L, 2.0));
        List<UserSongInteraction> userB = buildInteractions(Map.of(1L, 5.0, 4L, 4.0, 5L, 3.0));

        double sim = recommender.computePearsonSimilarity(userA, userB);
        // Jaccard: {1,2,3} ∩ {1,4,5} = {1} → 1/5 = 0.2
        assertEquals(0.2, sim, 0.001);
    }

    @Test
    @DisplayName("Pearson - 零共同歌曲 → 0")
    void pearson_noCommonSongs() {
        List<UserSongInteraction> userA = buildInteractions(Map.of(1L, 4.0, 2L, 3.0));
        List<UserSongInteraction> userB = buildInteractions(Map.of(3L, 5.0, 4L, 4.0));

        double sim = recommender.computePearsonSimilarity(userA, userB);
        assertEquals(0.0, sim, 0.001);
    }

    // ==================== computeUserPreference ====================

    @Test
    @DisplayName("偏好分 - 仅听过 → 基础分 1.0")
    void pref_base() {
        UserSongInteraction ui = new UserSongInteraction();
        ui.setPlayCount(1);
        ui.setIsLiked(false);
        ui.setLastPlayedAt(LocalDateTime.now());

        double score = recommender.computeUserPreference(ui);
        assertEquals(1.0 + Math.log1p(1) * 0.8 + 0.5, score, 0.01);
        assertTrue(score < 2.5, "基础分应低于 2.5");
    }

    @Test
    @DisplayName("偏好分 - 播放次数很多 → 加分但被封顶")
    void pref_highPlayCount() {
        UserSongInteraction ui = new UserSongInteraction();
        ui.setPlayCount(100);
        ui.setIsLiked(false);
        ui.setLastPlayedAt(LocalDateTime.now());

        double score = recommender.computeUserPreference(ui);
        assertTrue(score <= 5.0, "最高 5 分: " + score);
        assertTrue(score > 3.0, "100 次播放应显著高于基础分: " + score);
    }

    @Test
    @DisplayName("偏好分 - 点赞 → +2.0")
    void pref_liked() {
        UserSongInteraction ui = new UserSongInteraction();
        ui.setPlayCount(1);
        ui.setIsLiked(true);
        ui.setLastPlayedAt(LocalDateTime.now());

        double liked = recommender.computeUserPreference(ui);

        ui.setIsLiked(false);
        double notLiked = recommender.computeUserPreference(ui);

        assertEquals(2.0, liked - notLiked, 0.001);
    }

    @Test
    @DisplayName("偏好分 - 很久以前播放 → 时效性加分衰减为 0")
    void pref_oldPlay() {
        UserSongInteraction ui = new UserSongInteraction();
        ui.setPlayCount(5);
        ui.setIsLiked(false);
        ui.setLastPlayedAt(LocalDateTime.now().minusDays(60));

        double score = recommender.computeUserPreference(ui);
        // recencyBoost = max(0, 1 - 60/30) = 0 → 无加分
        double expected = 1.0 + Math.log1p(5) * 0.8;
        assertEquals(expected, score, 0.01);
    }

    @Test
    @DisplayName("偏好分 - 播放次数 null → 按 0 处理")
    void pref_nullPlayCount() {
        UserSongInteraction ui = new UserSongInteraction();
        ui.setPlayCount(null);
        ui.setIsLiked(false);
        ui.setLastPlayedAt(null);

        double score = recommender.computeUserPreference(ui);
        // 基础分 1.0 + 0 + 0 + 0
        assertEquals(1.0, score, 0.001);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造测试用的交互列表
     * Map: songId → 期望偏好评分（通过调整 play_count/liked 来近似）
     * 简化处理：用 liked=true + 调整 play_count log 值来得到接近的分数
     */
    private List<UserSongInteraction> buildInteractions(Map<Long, Double> expectedScores) {
        List<UserSongInteraction> result = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : expectedScores.entrySet()) {
            UserSongInteraction ui = new UserSongInteraction();
            ui.setSongId(entry.getKey());
            ui.setUserId(1L);
            // 反推 playCount: score = 1 + log1p(pc)*0.8 → pc ≈ exp((score-1)/0.8) - 1
            double target = entry.getValue();
            if (target > 3.0) {
                // 需要点赞: score = 1 + log1p(pc)*0.8 + 2.0 + 0.5 → log1p(pc) = (target-3.5)/0.8
                double needed = Math.max(0, (target - 3.5) / 0.8);
                ui.setPlayCount((int) Math.expm1(needed));
                ui.setIsLiked(true);
            } else {
                double needed = Math.max(0, (target - 1.5) / 0.8);
                ui.setPlayCount((int) Math.expm1(needed));
                ui.setIsLiked(false);
            }
            ui.setLastPlayedAt(LocalDateTime.now());
            result.add(ui);
        }
        return result;
    }
}
