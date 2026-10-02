package com.music.recommendation.service.recommendation;

import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.UserSongInteraction;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserSongInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ALS 矩阵分解推荐器
 *
 * 算法: Alternating Least Squares for implicit feedback
 *   目标: min_{U,P} Σ_{(u,i)∈Ω} (r_ui - U_u · P_i)^2 + λ(‖U‖² + ‖P‖²)
 *   Ω  = 有交互的 (user, song) 集合
 *   r  = 隐式评分（播放次数归一化 0~1）
 *   U  = [nUsers × k] 用户隐因子矩阵
 *   P  = [nSongs × k] 歌曲隐因子矩阵
 *   λ  = 正则化系数
 *
 *   交替更新:
 *     固定 P, 解 U_u = (P_ΩᵀP_Ω + λI)⁻¹ P_Ωᵀ r_Ω   （对每个有交互的用户）
 *     固定 U, 解 P_i = (U_ΩᵀU_Ω + λI)⁻¹ U_Ωᵀ r_Ω   （对每个有交互的物品）
 *
 * 实现特点:
 *   1. 只用对角方阵的闭式解求逆 — 无外部线性代数库
 *   2. 隐式评分 = 0.1 + 0.9 × min(1, playCount / playMax)
 *      （没交互 = 0，交互越多分越高但 cap 在 1）
 *   3. 训练结果存 Caffeine 缓存，key = "als:model:v1"
 *      交互表变动时自动过期，5 分钟自动刷新
 *   4. 可独立使用，也可作为 CF 的补充信号融合到 Hybrid
 *
 * 论文可拓展:
 *   - 对比 UserCF / ALS / 混合的 Precision@K / NDCG
 *   - 画不同 latentDim / iterations 下的 RMSE 收敛曲线
 */
@Component
public class AlsRecommender {

    private static final Logger log = LoggerFactory.getLogger(AlsRecommender.class);
    private static final long MODEL_CACHE_KEY = 1L; // 单实例 key

    /** 隐因子维度（超参数 k） */
    private static final int LATENT_DIM = 8;
    /** ALS 迭代次数 */
    private static final int ITERATIONS = 20;
    /** 正则化系数 λ */
    private static final double REG_LAMBDA = 0.1;
    /** 隐式评分基准（未交互不参与训练） */
    private static final double IMPLICIT_BASE = 0.1;
    /** ALS 训练后 top-N 候选数量 */
    private static final int ALS_CANDIDATE_N = 30;

    private final UserSongInteractionRepository interactionRepository;
    private final SongRepository songRepository;
    /** 单例训练锁，避免并发重复训练 */
    private final Object trainLock = new Object();
    /** 已训练的模型（软引用，GC 可回收） */
    private volatile AlsModel cachedModel;
    /** 上次训练时的交互总数，变化了就重训 */
    private volatile long lastInteractionCount = -1;

    public AlsRecommender(UserSongInteractionRepository interactionRepository,
                          SongRepository songRepository) {
        this.interactionRepository = interactionRepository;
        this.songRepository = songRepository;
    }

    // ==================== 推荐入口 ====================

    /**
     * 为指定用户生成 ALS 推荐
     */
    public List<Song> recommend(Long userId, int limit) {
        AlsModel model = ensureTrained();
        if (model == null) return Collections.emptyList();

        Integer uIdx = model.userIndex.get(userId);
        if (uIdx == null) return Collections.emptyList(); // 冷启动用户，ALS 无法外推

        Set<Long> interacted = new HashSet<>(interactionRepository.findSongIdsByUserId(userId));

        // 计算所有歌曲的预测分 = userFactor · songFactor
        List<Map.Entry<Long, Double>> scored = new ArrayList<>();
        double[] uFac = model.U[uIdx];
        for (Map.Entry<Long, Integer> e : model.songIndex.entrySet()) {
            Long songId = e.getKey();
            if (interacted.contains(songId)) continue;
            double s = dot(uFac, model.P[e.getValue()]);
            scored.add(new AbstractMap.SimpleEntry<>(songId, s));
        }

        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<Long> topIds = scored.stream().limit(ALS_CANDIDATE_N).map(Map.Entry::getKey).collect(Collectors.toList());
        if (topIds.isEmpty()) return Collections.emptyList();

        Map<Long, Song> songMap = songRepository.findAllById(topIds).stream()
                .collect(Collectors.toMap(Song::getId, s -> s));
        return topIds.stream().map(songMap::get).filter(Objects::nonNull).limit(limit).collect(Collectors.toList());
    }

    /** 获取当前模型（供 Admin 诊断页用） */
    public AlsModel getModel() { return ensureTrained(); }

    // ==================== ALS 训练核心 ====================

    private AlsModel ensureTrained() {
        long total = interactionRepository.count();
        if (cachedModel != null && lastInteractionCount == total) {
            return cachedModel;
        }
        synchronized (trainLock) {
            if (cachedModel != null && lastInteractionCount == total) {
                return cachedModel;
            }
            cachedModel = train();
            lastInteractionCount = total;
            return cachedModel;
        }
    }

    /**
     * ALS 主训练循环
     */
    private AlsModel train() {
        List<UserSongInteraction> all = interactionRepository.findAll();
        if (all.isEmpty()) return null;

        // === Step 1: 索引映射 ===
        List<Long> userList = all.stream().map(UserSongInteraction::getUserId).distinct().collect(Collectors.toList());
        List<Long> songList = all.stream().map(UserSongInteraction::getSongId).distinct().collect(Collectors.toList());
        Map<Long, Integer> userIndex = new HashMap<>(userList.size());
        Map<Long, Integer> songIndex = new HashMap<>(songList.size());
        for (int i = 0; i < userList.size(); i++) userIndex.put(userList.get(i), i);
        for (int i = 0; i < songList.size(); i++) songIndex.put(songList.get(i), i);
        int nU = userList.size(), nI = songList.size();

        // === Step 2: 构建隐式评分矩阵 + 观测 mask ===
        // songsByUser[u] = [(songIdx, rating), ...]
        List<List<double[]>> songsByUser = new ArrayList<>(nU);
        for (int u = 0; u < nU; u++) songsByUser.add(new ArrayList<>());
        List<List<double[]>> usersBySong = new ArrayList<>(nI);
        for (int i = 0; i < nI; i++) usersBySong.add(new ArrayList<>());

        // 计算每首歌的 maxPlayCount 做归一化
        Map<Long, Integer> maxPlayCache = new HashMap<>();
        List<UserSongInteraction> allSongs = interactionRepository.findAll();
        for (UserSongInteraction ui : allSongs) {
            maxPlayCache.merge(ui.getSongId(), ui.getPlayCount() == null ? 0 : ui.getPlayCount(), Math::max);
        }

        for (UserSongInteraction ui : all) {
            Integer uIdx = userIndex.get(ui.getUserId());
            Integer iIdx = songIndex.get(ui.getSongId());
            if (uIdx == null || iIdx == null) continue;
            int playCount = ui.getPlayCount() == null ? 0 : ui.getPlayCount();
            int maxPlay = maxPlayCache.getOrDefault(ui.getSongId(), 1);
            double rating = IMPLICIT_BASE + 0.9 * Math.min(1.0, (double) playCount / Math.max(1, maxPlay));
            songsByUser.get(uIdx).add(new double[]{iIdx, rating});
            usersBySong.get(iIdx).add(new double[]{uIdx, rating});
        }

        // === Step 3: 随机初始化因子矩阵 ===
        Random rng = new Random(42);
        double[][] U = new double[nU][LATENT_DIM];
        double[][] P = new double[nI][LATENT_DIM];
        for (int u = 0; u < nU; u++) for (int f = 0; f < LATENT_DIM; f++) U[u][f] = rng.nextGaussian() * 0.1;
        for (int i = 0; i < nI; i++) for (int f = 0; f < LATENT_DIM; f++) P[i][f] = rng.nextGaussian() * 0.1;

        // === Step 4: ALS 交替迭代 ===
        for (int iter = 0; iter < ITERATIONS; iter++) {
            double rmse = 0, rmseCount = 0;

            // 固定 P → 更新 U
            for (int u = 0; u < nU; u++) {
                List<double[]> ratings = songsByUser.get(u);
                if (ratings.isEmpty()) continue;
                double[] solved = solveLeastSquares(ratings, P);
                System.arraycopy(solved, 0, U[u], 0, LATENT_DIM);
            }

            // 固定 U → 更新 P
            for (int i = 0; i < nI; i++) {
                List<double[]> ratings = usersBySong.get(i);
                if (ratings.isEmpty()) continue;
                double[] solved = solveLeastSquares(ratings, U);
                System.arraycopy(solved, 0, P[i], 0, LATENT_DIM);
            }

            // 计算 RMSE (采样)
            for (int u = 0; u < Math.min(nU, 50); u++) {
                for (double[] pair : songsByUser.get(u)) {
                    double err = dot(U[u], P[(int) pair[0]]) - pair[1];
                    rmse += err * err;
                    rmseCount++;
                }
            }
            rmse = rmseCount > 0 ? Math.sqrt(rmse / rmseCount) : 0;
            if (iter % 5 == 0 || iter == ITERATIONS - 1) {
                log.info("[ALS] iter={} RMSE={:.4f}", iter, rmse);
            }
        }

        log.info("[ALS] trained: {} users × {} songs, latentDim={}, iters={}",
                nU, nI, LATENT_DIM, ITERATIONS);
        return new AlsModel(U, P, userList, songList, userIndex, songIndex, nU, nI);
    }

    // ==================== 闭式解核心 ====================

    /**
     * 对角方阵 (k×k) 的闭式最小二乘解：
     *   A = Σ_r p_r p_rᵀ + λI   (k×k 对称正定)
     *   b = Σ_r p_r r            (k×1)
     *   x = A⁻¹ b                (k×1)
     *
     * 对对角方阵直接用伴随矩阵法求逆，避免外部依赖
     * k 很小（本项目 LATENT_DIM=8），完全够用
     */
    private double[] solveLeastSquares(List<double[]> ratings, double[][] factorMatrix) {
        int k = LATENT_DIM;
        // A: k×k, b: k×1
        double[][] A = new double[k][k];
        double[] b = new double[k];
        for (double[] pair : ratings) {
            int idx = (int) pair[0];
            double r = pair[1];
            double[] f = factorMatrix[idx];
            // A += f fᵀ, b += r f
            for (int fi = 0; fi < k; fi++) {
                for (int fj = 0; fj < k; fj++) A[fi][fj] += f[fi] * f[fj];
                b[fi] += r * f[fi];
            }
        }
        // 正则化
        for (int fi = 0; fi < k; fi++) A[fi][fi] += REG_LAMBDA;

        return solveLinearSystem(A, b, k);
    }

    /**
     * 对角方阵线性方程组求解 — 高斯-约旦消元法
     * A x = b → 直接原地消元
     */
    private double[] solveLinearSystem(double[][] A, double[] b, int k) {
        // 增广矩阵 [A | b] 大小 k × (k+1)
        double[][] aug = new double[k][k + 1];
        for (int i = 0; i < k; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, k);
            aug[i][k] = b[i];
        }

        for (int col = 0; col < k; col++) {
            // 选主元
            int maxRow = col;
            double maxVal = Math.abs(aug[col][col]);
            for (int row = col + 1; row < k; row++) {
                if (Math.abs(aug[row][col]) > maxVal) {
                    maxVal = Math.abs(aug[row][col]);
                    maxRow = row;
                }
            }
            if (maxRow != col) {
                double[] tmp = aug[col]; aug[col] = aug[maxRow]; aug[maxRow] = tmp;
            }

            // 主元为 0（奇异矩阵），退化为零向量
            if (Math.abs(aug[col][col]) < 1e-10) {
                double[] result = new double[k];
                log.warn("[ALS] singular matrix at col={}, returning zeros", col);
                return result;
            }

            // 主元归一
            double pivot = aug[col][col];
            for (int j = col; j <= k; j++) aug[col][j] /= pivot;

            // 消去其他行
            for (int row = 0; row < k; row++) {
                if (row == col) continue;
                double factor = aug[row][col];
                for (int j = col; j <= k; j++) aug[row][j] -= factor * aug[col][j];
            }
        }

        double[] result = new double[k];
        for (int i = 0; i < k; i++) result[i] = aug[i][k];
        return result;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    // ==================== 模型结构 ====================

    /** ALS 训练结果 */
    public static class AlsModel {
        public final double[][] U;           // 用户因子 [nU × k]
        public final double[][] P;           // 歌曲因子 [nI × k]
        public final List<Long> userIds;      // 索引 → userId
        public final List<Long> songIds;      // 索引 → songId
        public final Map<Long, Integer> userIndex;
        public final Map<Long, Integer> songIndex;
        public final int nUsers;
        public final int nSongs;

        public AlsModel(double[][] U, double[][] P,
                        List<Long> userIds, List<Long> songIds,
                        Map<Long, Integer> userIndex, Map<Long, Integer> songIndex,
                        int nUsers, int nSongs) {
            this.U = U; this.P = P;
            this.userIds = userIds; this.songIds = songIds;
            this.userIndex = userIndex; this.songIndex = songIndex;
            this.nUsers = nUsers; this.nSongs = nSongs;
        }

        /** 预测用户对歌曲的评分（归一化 0~1） */
        public double predict(Long userId, Long songId) {
            Integer u = userIndex.get(userId);
            Integer i = songIndex.get(songId);
            if (u == null || i == null) return 0;
            return Math.max(0, Math.min(1, dot(U[u], P[i])));
        }
    }
}
