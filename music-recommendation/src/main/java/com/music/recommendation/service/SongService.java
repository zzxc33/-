package com.music.recommendation.service;

import com.music.recommendation.entity.Song;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 歌曲服务接口
 */
public interface SongService {

    /** 获取所有歌曲 */
    List<Song> getAllSongs();

    /** 分页获取歌曲 */
    Map<String, Object> getSongsPage(int page, int size);

    /** 根据ID获取歌曲 */
    Optional<Song> getSongById(Long id);

    /** 搜索歌曲 */
    List<Song> searchSongs(String keyword);

    /** 获取热门歌曲 */
    List<Song> getHotSongs(int limit);

    /** 获取最新歌曲 */
    List<Song> getNewSongs(int limit);

    /** 获取推荐歌曲（混合推荐引擎） */
    List<Song> getRecommendedSongs(Long userId, int limit);

    /** 根据风格获取歌曲 */
    List<Song> getSongsByGenre(String genre);

    /** 记录用户播放歌曲 */
    void recordPlay(Long userId, Long songId);

    /** 切换用户点赞歌曲 */
    void recordLike(Long userId, Long songId);

    /** 获取相关歌曲 */
    List<Song> getRelatedSongs(Long songId, int limit);

    /** 获取所有风格 */
    List<String> getAllGenres();

    /** 统计用户总播放次数 */
    long countTotalPlayed(Long userId);

    /** 统计用户喜欢的歌曲数 */
    long countTotalLiked(Long userId);

    /** 获取用户喜欢的歌曲列表 */
    List<Song> getLikedSongs(Long userId);

    /** 检查用户是否已点赞某首歌 */
    boolean isLiked(Long userId, Long songId);

    /** 根据歌曲ID批量获取歌曲 */
    List<Song> getSongsByIds(List<Long> ids);

    /** 统计用户风格分布 */
    Map<String, Long> getUserGenreStats(Long userId);

    /** 根据歌手精确查找（按播放量排序） */
    List<Song> getSongsByArtist(String artist);

    /** 全局播放量排行榜 Top N */
    List<Song> getRankingByPlayCount(int limit);

    /** 随机获取一首歌曲 */
    Song getRandomSong();

    /** 最愛歌手榜（按播放量+點贊加權排序） */
    Map<String, Long> getUserTopArtists(Long userId);

    /** 最近播放列表（按 lastPlayedAt 倒序） */
    List<Song> getUserRecentPlays(Long userId, int limit);

    /** 7天播放熱力圖數據（每小時的播放次數，返回 7×24 矩陣展平為列表） */
    List<int[]> getPlayHeatmapData(Long userId);
}
