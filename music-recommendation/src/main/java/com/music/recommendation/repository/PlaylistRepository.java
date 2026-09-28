package com.music.recommendation.repository;

import com.music.recommendation.entity.Playlist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    /** 获取用户的歌单 */
    List<Playlist> findByUserId(Long userId);

    /** 获取公开歌单 */
    List<Playlist> findByIsPublicTrue(Pageable pageable);

    /** 搜索歌单 */
    List<Playlist> findByNameContainingAndIsPublicTrue(String keyword);

    /** 热门歌单 */
    List<Playlist> findAllByOrderByPlayCountDesc(Pageable pageable);
}