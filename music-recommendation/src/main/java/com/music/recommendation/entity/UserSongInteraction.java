package com.music.recommendation.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_song_interactions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "song_id"}))
public class UserSongInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    /** 播放次数 */
    @Column(name = "play_count", columnDefinition = "INT DEFAULT 0")
    private Integer playCount = 0;

    /** 是否喜欢（点赞） */
    @Column(name = "is_liked", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isLiked = false;

    @Column(name = "last_played_at")
    private LocalDateTime lastPlayedAt;

    @Column(name = "liked_at")
    private LocalDateTime likedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (playCount == null) playCount = 0;
        if (isLiked == null) isLiked = false;
    }

    @PreUpdate
    protected void onUpdate() {
        lastPlayedAt = LocalDateTime.now();
    }
}