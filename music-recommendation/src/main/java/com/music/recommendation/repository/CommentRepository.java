package com.music.recommendation.repository;

import com.music.recommendation.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 获取某首歌的所有评论（按时间倒序） */
    List<Comment> findBySongIdOrderByCreatedAtDesc(Long songId);

    /** 统计某首歌的评论数 */
    long countBySongId(Long songId);

    /** 获取用户的所有评论 */
    List<Comment> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 批量获取多首歌的评论数 */
    @Query("SELECT c.songId, COUNT(c) FROM Comment c WHERE c.songId IN :songIds GROUP BY c.songId")
    List<Object[]> countBySongIdIn(@Param("songIds") List<Long> songIds);
}