package com.music.recommendation.service.recommendation;

import com.music.recommendation.entity.Song;

import java.util.List;

/**
 * 混合推荐引擎接口
 */
public interface HybridRecommenderService {

    /**
     * 为指定用户生成混合推荐
     * @param userId 用户ID
     * @param limit 推荐数量
     * @return 推荐歌曲列表
     */
    List<Song> recommend(Long userId, int limit);
}
