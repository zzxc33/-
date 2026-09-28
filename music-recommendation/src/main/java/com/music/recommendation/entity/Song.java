package com.music.recommendation.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "songs")
public class Song {

    /** 复用 Spring Boot 内置 Jackson，安全处理所有转义和特殊字符 */
    private static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String artist;

    @Column(length = 200)
    private String album;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "audio_url", length = 500)
    private String audioUrl;

    @Column(length = 50)
    private String genre;

    @Column(name = "duration", length = 20)
    private String duration;

    @Column(name = "play_count", columnDefinition = "BIGINT DEFAULT 0")
    private Long playCount = 0L;

    @Column(name = "like_count", columnDefinition = "BIGINT DEFAULT 0")
    private Long likeCount = 0L;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (playCount == null) playCount = 0L;
        if (likeCount == null) likeCount = 0L;
    }

    /** 生成外部播放链接（网易云音乐搜索） */
    public String getExternalUrl() {
        try {
            String query = java.net.URLEncoder.encode(
                (title != null ? title : "") + " " + (artist != null ? artist : ""),
                "UTF-8"
            );
            return "https://music.163.com/#/search/m/?s=" + query;
        } catch (Exception e) {
            return "https://music.163.com";
        }
    }

    /**
     * 转换为 JSON 字符串（用于 Thymeleaf 内联 data-song="${song.toJson()}"）
     *
     * 使用 Jackson ObjectMapper 序列化，自动处理：
     *  - 双引号、反斜杠等特殊字符的正确转义（避免 JSON 注入）
     *  - null 值安全（序列化为 ""）
     *  - 中文等 Unicode 字符
     */
    public String toJson() {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("title", nullSafe(title));
            m.put("artist", nullSafe(artist));
            m.put("album", nullSafe(album));
            m.put("coverUrl", nullSafe(coverUrl));
            m.put("audioUrl", nullSafe(audioUrl));
            m.put("genre", nullSafe(genre));
            m.put("duration", nullSafe(duration));
            m.put("externalUrl", getExternalUrl());
            m.put("playCount", playCount != null ? playCount : 0);
            m.put("likeCount", likeCount != null ? likeCount : 0);
            m.put("description", nullSafe(description));
            return MAPPER.writeValueAsString(m);
        } catch (Exception e) {
            // 兜底：序列化异常时返回安全的空对象
            return "{}";
        }
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }
}
