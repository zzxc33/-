package com.music.recommendation.repository;

import com.music.recommendation.entity.Song;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SongRepository extends JpaRepository<Song, Long> {

    /** 根据歌名搜索 */
    List<Song> findByTitleContaining(String keyword);

    /** 根据歌手模糊搜索 */
    List<Song> findByArtistContaining(String keyword);

    /** 根据歌手精确查找 */
    List<Song> findByArtistOrderByPlayCountDesc(String artist);

    /** 根据风格分类 */
    List<Song> findByGenre(String genre);

    /** 获取热门歌曲（按播放量排序） */
    List<Song> findAllByOrderByPlayCountDesc(Pageable pageable);

    /** 获取最新歌曲 */
    List<Song> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 获取推荐歌曲（按喜欢数排序） */
    List<Song> findAllByOrderByLikeCountDesc(Pageable pageable);

    /** 搜索歌名或歌手 */
    @Query("SELECT s FROM Song s WHERE s.title LIKE %:keyword% OR s.artist LIKE %:keyword%")
    List<Song> searchByKeyword(String keyword, Pageable pageable);

    /** 获取所有不重复的风格 */
    @Query("SELECT DISTINCT s.genre FROM Song s WHERE s.genre IS NOT NULL")
    List<String> findDistinctGenres();

    /** 统计所有歌曲的总播放量 */
    @Query("SELECT COALESCE(SUM(s.playCount), 0) FROM Song s")
    Long sumTotalPlayCount();

    /** 统计各风格的歌曲数量 */
    @Query("SELECT s.genre, COUNT(s) FROM Song s GROUP BY s.genre ORDER BY COUNT(s) DESC")
    List<Object[]> findGenreStats();
}