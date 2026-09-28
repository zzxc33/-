package com.music.recommendation.repository;

import com.music.recommendation.entity.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Long> {

    /** 获取歌单中的所有歌曲 */
    List<PlaylistSong> findByPlaylistIdOrderBySortOrderAsc(Long playlistId);

    /** 检查歌曲是否已在歌单中 */
    boolean existsByPlaylistIdAndSongId(Long playlistId, Long songId);

    /** 删除歌单中的某首歌曲 */
    void deleteByPlaylistIdAndSongId(Long playlistId, Long songId);

    /** 获取歌单中的歌曲数量 */
    long countByPlaylistId(Long playlistId);
}