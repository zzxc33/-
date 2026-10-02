package com.music.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 自适应推荐引擎配置属性
 *
 * 对应 application.yml:
 *   recommender.adaptive.tiers[*]   — 按交互数分档的基础权重
 *   recommender.dynamic.*            — 动态调整因子（CF 惩罚 / 活跃度修正）
 *
 * 档位设计（5 档）：
 *   档0: 0 次交互     — 内容为主 (CB 0.45)，不启用 CF
 *   档1: 4-10 次      — 稀疏，CB+热门平衡
 *   档2: 11-30 次     — 普通用户，CF 渐起
 *   档3: 31-100 次    — 活跃用户，CF 主导
 *   档4: 100+ 次      — 专家用户，CF 强势
 */
@ConfigurationProperties(prefix = "recommender")
public class RecommenderProperties {

    private Adaptive adaptive = new Adaptive();
    private Dynamic dynamic = new Dynamic();

    public Adaptive getAdaptive() { return adaptive; }
    public void setAdaptive(Adaptive adaptive) { this.adaptive = adaptive; }
    public Dynamic getDynamic() { return dynamic; }
    public void setDynamic(Dynamic dynamic) { this.dynamic = dynamic; }

    public static class Adaptive {
        /** 是否启用自适应（false 则回退引擎硬编码默认） */
        private boolean enabled = true;
        /** 冷启动时热门占比 */
        private double coldStartHotRatio = 0.5;
        /** 按交互数升序排列的档位列表 */
        private List<Tier> tiers = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getColdStartHotRatio() { return coldStartHotRatio; }
        public void setColdStartHotRatio(double v) { this.coldStartHotRatio = v; }
        public List<Tier> getTiers() { return tiers; }
        public void setTiers(List<Tier> tiers) { this.tiers = tiers; }
    }

    public static class Tier {
        /** 该档最少交互数（阈值） */
        private long minInteractions;
        /** CF 权重 */
        private double cf;
        /** 内容推荐权重 */
        private double cb;
        /** 热度权重 */
        private double popularity;
        /** 探索权重 */
        private double explore;

        public long getMinInteractions() { return minInteractions; }
        public void setMinInteractions(long minInteractions) { this.minInteractions = minInteractions; }
        public double getCf() { return cf; }
        public void setCf(double cf) { this.cf = cf; }
        public double getCb() { return cb; }
        public void setCb(double cb) { this.cb = cb; }
        public double getPopularity() { return popularity; }
        public void setPopularity(double popularity) { this.popularity = popularity; }
        public double getExplore() { return explore; }
        public void setExplore(double explore) { this.explore = explore; }

        public double sum() { return cf + cb + popularity + explore; }
    }

    public static class Dynamic {
        /** CF 返回少于 cfMinResults 首时，CF 权重 × (1 - cfEmptyPenalty) */
        private double cfEmptyPenalty = 0.3;
        /** CF 最少返回数阈值 */
        private int cfMinResults = 3;
        /** 活跃天数阈值（多少天内有过交互算活跃） */
        private int activeDays = 7;
        /** 沉寂天数阈值 */
        private int inactiveDays = 30;
        /** 活跃用户 CF 权重额外提升 */
        private double cfBoostActive = 0.10;
        /** 沉寂用户 CF 权重额外削减 */
        private double cfPenaltyInactive = 0.10;

        public double getCfEmptyPenalty() { return cfEmptyPenalty; }
        public void setCfEmptyPenalty(double v) { this.cfEmptyPenalty = v; }
        public int getCfMinResults() { return cfMinResults; }
        public void setCfMinResults(int v) { this.cfMinResults = v; }
        public int getActiveDays() { return activeDays; }
        public void setActiveDays(int v) { this.activeDays = v; }
        public int getInactiveDays() { return inactiveDays; }
        public void setInactiveDays(int v) { this.inactiveDays = v; }
        public double getCfBoostActive() { return cfBoostActive; }
        public void setCfBoostActive(double v) { this.cfBoostActive = v; }
        public double getCfPenaltyInactive() { return cfPenaltyInactive; }
        public void setCfPenaltyInactive(double v) { this.cfPenaltyInactive = v; }
    }
}
