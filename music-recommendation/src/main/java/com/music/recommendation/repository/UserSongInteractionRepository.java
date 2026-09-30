package com.music.recommendation.repository;

import com.music.recommendation.entity.UserSongInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserSongInteractionRepository extends JpaRepository<UserSongInteraction, Long> {

    /** 获取用户对某首歌的交互记录 */
    Optional<UserSongInteraction> findByUserIdAndSongId(Long userId, Long songId);

    /** 获取用户交互过的所有歌曲ID */
    @Query("SELECT u.songId FROM UserSongInteraction u WHERE u.userId = :userId")
    List<Long> findSongIdsByUserId(@Param("userId") Long userId);

    /** 获取用户交互过得所有歌曲（含详情） */
    List<UserSongInteraction> findByUserId(Long userId);

    /** 获取某首歌的所有交互记录 */
    List<UserSongInteraction> findBySongId(Long songId);

    /** 获取喜欢某首歌的所有用户ID（协同过滤用） */
    @Query("SELECT u.userId FROM UserSongInteraction u WHERE u.songId = :songId AND u.isLiked = true")
    List<Long> findUserIdsByLikedSongId(@Param("songId") Long songId);

    /** 获取与目标用户有相同歌曲交互的其他用户ID */
    @Query("SELECT DISTINCT u.userId FROM UserSongInteraction u WHERE u.songId IN " +
           "(SELECT u2.songId FROM UserSongInteraction u2 WHERE u2.userId = :userId) AND u.userId <> :userId")
    List<Long> findSimilarUserIds(@Param("userId") Long userId);

    /** 统计用户交互歌曲数量 */
    long countByUserId(Long userId);

    /** 统计用户总播放次数 */
    @Query("SELECT COALESCE(SUM(u.playCount), 0) FROM UserSongInteraction u WHERE u.userId = :userId")
    long sumPlayCountByUserId(@Param("userId") Long userId);

    /** 统计用户喜欢的歌曲数量 */
    long countByUserIdAndIsLikedTrue(Long userId);

    /** 获取用户喜欢的歌曲ID列表 */
    @Query("SELECT u.songId FROM UserSongInteraction u WHERE u.userId = :userId AND u.isLiked = true")
    List<Long> findLikedSongIdsByUserId(@Param("userId") Long userId);

    /** 获取用户喜欢的歌曲交互记录 */
    List<UserSongInteraction> findByUserIdAndIsLikedTrueOrderByLikedAtDesc(Long userId);

    /** 批量获取多个用户的交互记录（替代逐用户循环查询，解决 N+1） */
    @Query("SELECT u FROM UserSongInteraction u WHERE u.userId IN :userIds")
    List<UserSongInteraction> findByUserIdIn(@Param("userIds") Collection<Long> userIds);

    /** 批量获取多首歌曲的交互记录 */
    @Query("SELECT u FROM UserSongInteraction u WHERE u.songId IN :songIds")
    List<UserSongInteraction> findBySongIdIn(@Param("songIds") Collection<Long> songIds);

    /** 精确计算喜欢/交互过某首歌的独立用户数（物品协同过滤算余弦相似度用） */
    @Query("SELECT COUNT(DISTINCT u.userId) FROM UserSongInteraction u WHERE u.songId = :songId")
    long countDistinctUsersBySongId(@Param("songId") Long songId);

    /** 批量计算多首歌的独立用户数（避免循环内 N+1） */
    @Query("SELECT u.songId, COUNT(DISTINCT u.userId) FROM UserSongInteraction u WHERE u.songId IN :songIds GROUP BY u.songId")
    List<Object[]> countDistinctUsersBySongIds(@Param("songIds") Collection<Long> songIds);
}