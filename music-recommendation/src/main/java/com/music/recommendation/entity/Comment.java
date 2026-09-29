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
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "like_count")
    private Long likeCount = 0L;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Transient
    private String username;

    @Transient
    private String userAvatar;

    public Comment(Long songId, Long userId, String content) {
        this.songId = songId;
        this.userId = userId;
        this.content = content;
        this.likeCount = 0L;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (likeCount == null) {
            likeCount = 0L;
        }
    }
}
