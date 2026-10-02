package com.music.recommendation.controller;

import com.music.recommendation.common.ApiResponse;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import com.music.recommendation.service.recommendation.CollaborativeRecommender;
import com.music.recommendation.service.recommendation.ContentBasedRecommender;
import com.music.recommendation.service.recommendation.AlsRecommender;
import com.music.recommendation.service.recommendation.HybridRecommenderService;
import com.music.recommendation.service.recommendation.RecommendationEvaluator;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐引擎管理 REST API（仅管理员可访问）
 *
 * 接口清单：
 *   GET /admin/api/recommend/diagnose/{userId}
 *     综合诊断：三种算法各返回什么歌 + 权重分配 + 最终融合结果
 *
 *   GET /admin/api/recommend/hybrid?userId=X&limit=N
 *     混合推荐最终结果
 *
 *   GET /admin/api/recommend/cf?userId=X&limit=N
 *     单独调用协同过滤
 *
 *   GET /admin/api/recommend/cb?userId=X&limit=N
 *     单独调用内容推荐
 *
 *   GET /admin/api/recommend/stats
 *     全局统计（交互数/活跃用户/冷启动用户）
 *
 *   GET /admin/api/recommend/evaluate
 *     离线评估（Precision/Recall/NDCG）
 */
@RestController
@RequestMapping("/admin/api/recommend")
public class AdminRecommendController {

    private final HybridRecommenderService hybridRecommender;
    private final CollaborativeRecommender cfRecommender;
    private final AlsRecommender alsRecommender;
    private final ContentBasedRecommender cbRecommender;
    private final UserSongInteractionRepository interactionRepository;
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final RecommendationEvaluator evaluator;

    public AdminRecommendController(HybridRecommenderService hybridRecommender,
                                    CollaborativeRecommender cfRecommender,
                                    AlsRecommender alsRecommender,
                                    ContentBasedRecommender cbRecommender,
                                    UserSongInteractionRepository interactionRepository,
                                    UserRepository userRepository,
                                    SongRepository songRepository,
                                    RecommendationEvaluator evaluator) {
        this.hybridRecommender = hybridRecommender;
        this.cfRecommender = cfRecommender;
        this.alsRecommender = alsRecommender;
        this.cbRecommender = cbRecommender;
        this.interactionRepository = interactionRepository;
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.evaluator = evaluator;
    }

    // ============================================
    //  1. 综合诊断
    // ============================================

    @GetMapping("/diagnose/{userId}")
    public ApiResponse<Map<String, Object>> diagnose(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "8") int limit) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 用户信息
        User user = userRepository.findById(userId).orElse(null);
        result.put("user", user == null ? null : Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        ));

        // 交互统计
        long interactionCount = interactionRepository.countByUserId(userId);
        result.put("interactionCount", interactionCount);
        result.put("interactionDetail", Map.of(
                "playCount", interactionRepository.sumPlayCountByUserId(userId),
                "likedCount", interactionRepository.countByUserIdAndIsLikedTrue(userId),
                "songIds", interactionRepository.findSongIdsByUserId(userId)
        ));

        // 判断用户处于哪个阶段
        String userStage;
        if (interactionCount == 0) {
            userStage = "冷启动（无交互）";
        } else if (interactionCount <= 5) {
            userStage = "数据稀疏（内容推荐主导）";
        } else {
            userStage = "正常数据（协同过滤 + 内容推荐融合）";
        }
        result.put("userStage", userStage);

        // 四种算法单独推荐
        List<Song> cfResults = cfRecommender.recommend(userId, limit);
        List<Song> alsResults = alsRecommender.recommend(userId, limit);
        List<Song> cbResults = cbRecommender.recommend(userId, limit);
        List<Song> hybridResults = hybridRecommender.recommend(userId, limit);

        result.put("collaborativeFiltering", buildAlgoSection("协同过滤 (UserCF+ItemCF)", cfResults));
        result.put("alsMatrixFactorization", buildAlgoSection("ALS 矩阵分解 (隐因子)", alsResults));
        result.put("contentBased", buildAlgoSection("内容推荐 (风格+歌手+专辑)", cbResults));
        result.put("hybridFinal", buildAlgoSection("混合推荐最终结果", hybridResults));

        // ALS 模型信息
        AlsRecommender.AlsModel model = alsRecommender.getModel();
        if (model != null) {
            result.put("alsModel", Map.of(
                    "nUsers", model.nUsers,
                    "nSongs", model.nSongs,
                    "latentDim", model.U.length > 0 ? model.U[0].length : 0
            ));
        }

        // 档位信息
        String tierName;
        if (interactionCount == 0) tierName = "冷启动 (档0之前)";
        else if (interactionCount <= 3) tierName = "档0: 极稀疏 (0-3)";
        else if (interactionCount <= 10) tierName = "档1: 稀疏 (4-10)";
        else if (interactionCount <= 30) tierName = "档2: 普通 (11-30)";
        else if (interactionCount <= 100) tierName = "档3: 活跃 (31-100)";
        else tierName = "档4: 专家 (100+)";
        result.put("adaptiveTier", tierName);

        return ApiResponse.ok(result);
    }

    private Map<String, Object> buildAlgoSection(String label, List<Song> songs) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("label", label);
        section.put("resultCount", songs.size());
        section.put("songs", songs.stream().map(this::songSummary).collect(Collectors.toList()));
        return section;
    }

    private Map<String, Object> songSummary(Song s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("title", s.getTitle());
        m.put("artist", s.getArtist());
        m.put("genre", s.getGenre());
        m.put("playCount", s.getPlayCount());
        m.put("likeCount", s.getLikeCount());
        return m;
    }

    // ============================================
    //  2. 混合推荐（最终融合）
    // ============================================

    @GetMapping("/hybrid")
    public ApiResponse<Map<String, Object>> hybrid(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(buildResult("混合推荐", hybridRecommender.recommend(userId, limit)));
    }

    // ============================================
    //  3. 协同过滤单独调用
    // ============================================

    @GetMapping("/cf")
    public ApiResponse<Map<String, Object>> cf(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(buildResult("协同过滤 (UserCF + ItemCF)",
                cfRecommender.recommend(userId, limit)));
    }

    // ============================================
    //  4. ALS 矩阵分解单独调用
    // ============================================

    @GetMapping("/als")
    public ApiResponse<Map<String, Object>> als(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(buildResult("ALS 矩阵分解 (隐因子)",
                alsRecommender.recommend(userId, limit)));
    }

    // ============================================
    //  5. 内容推荐单独调用
    // ============================================

    @GetMapping("/cb")
    public ApiResponse<Map<String, Object>> cb(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(buildResult("内容推荐 (风格+歌手+专辑)",
                cbRecommender.recommend(userId, limit)));
    }

    private Map<String, Object> buildResult(String label, List<Song> songs) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("algorithm", label);
        r.put("count", songs.size());
        r.put("songs", songs.stream().map(this::songSummary).collect(Collectors.toList()));
        return r;
    }

    // ============================================
    //  5. 全局统计
    // ============================================

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> result = new LinkedHashMap<>();

        long totalUsers = userRepository.count();
        long totalSongs = songRepository.count();
        long totalInteractions = interactionRepository.count();

        // 活跃用户（有交互的用户数）
        Set<Long> activeUserIds = interactionRepository.findAll().stream()
                .map(ui -> {
                    try { return ui.getUserId(); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 冷启动用户（注册但无交互）
        long coldStartUsers = totalUsers - activeUserIds.size();

        result.put("totalUsers", totalUsers);
        result.put("totalSongs", totalSongs);
        result.put("totalInteractions", totalInteractions);
        result.put("activeUsers", activeUserIds.size());
        result.put("coldStartUsers", coldStartUsers);

        // 每个活跃用户的交互条数
        Map<Long, Long> perUser = new LinkedHashMap<>();
        for (Long uid : activeUserIds) {
            perUser.put(uid, interactionRepository.countByUserId(uid));
        }
        result.put("interactionsPerUser", perUser);

        // 歌曲风格分布
        List<Object[]> genreRows = songRepository.findGenreStats();
        Map<String, Long> genreDist = new LinkedHashMap<>();
        for (Object[] row : genreRows) {
            String g = row[0] != null ? row[0].toString() : "其他";
            genreDist.put(g, (Long) row[1]);
        }
        result.put("genreDistribution", genreDist);

        return ApiResponse.ok(result);
    }

    // ============================================
    //  6. 推荐质量离线评估
    // ============================================

    /**
     * 执行完整的离线评估，返回三种算法的 Precision@K / Recall@K / NDCG@K
     */
    @GetMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate() {
        long start = System.currentTimeMillis();

        List<RecommendationEvaluator.EvalResult> results = evaluator.evaluateAll();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", new Date().toString());
        response.put("method", "Hold-Out (20% 留作测试集)");
        response.put("algorithms", results.stream()
                .map(RecommendationEvaluator.EvalResult::toMap)
                .collect(Collectors.toList()));
        response.put("totalMs", System.currentTimeMillis() - start);

        return ApiResponse.ok(response);
    }
}
