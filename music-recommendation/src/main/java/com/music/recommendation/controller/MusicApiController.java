package com.music.recommendation.controller;

import com.music.recommendation.common.ApiResponse;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.UserRepository;
import com.music.recommendation.service.PlaylistService;
import com.music.recommendation.service.SongService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 小程序/H5 端 REST API
 * 统一返回 JSON，路径前缀 /api
 */
@RestController
@RequestMapping("/api")
public class MusicApiController {

    private final SongService songService;
    private final PlaylistService playlistService;
    private final UserRepository userRepository;

    public MusicApiController(SongService songService,
                              PlaylistService playlistService,
                              UserRepository userRepository) {
        this.songService = songService;
        this.playlistService = playlistService;
        this.userRepository = userRepository;
    }

    // ==================== 首页聚合接口 ====================

    /** 首页一次性返回：推荐 + 热门 + 最新 + 风格 */
    @GetMapping("/home")
    public ApiResponse<Map<String, Object>> home(
            @RequestParam(required = false, defaultValue = "0") Long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("recommend", songService.getRecommendedSongs(userId, 12));
        data.put("hot", songService.getHotSongs(10));
        data.put("new", songService.getNewSongs(10));
        data.put("genres", songService.getAllGenres());
        return ApiResponse.ok(data);
    }

    // ==================== 推荐 ====================

    @GetMapping("/recommend")
    public ApiResponse<List<?>> recommend(
            @RequestParam(required = false, defaultValue = "0") Long userId,
            @RequestParam(required = false, defaultValue = "12") int limit) {
        return ApiResponse.ok(songService.getRecommendedSongs(userId, limit));
    }

    // ==================== 歌曲 ====================

    @GetMapping("/songs")
    public ApiResponse<Map<String, Object>> songs(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);
        Map<String, Object> data = new HashMap<>();
        data.put("songs", songService.getSongsPage(safePage, safeSize));
        return ApiResponse.ok(data);
    }

    @GetMapping("/songs/hot")
    public ApiResponse<List<?>> hotSongs(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ApiResponse.ok(songService.getHotSongs(clampLimit(limit)));
    }

    @GetMapping("/songs/new")
    public ApiResponse<List<?>> newSongs(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ApiResponse.ok(songService.getNewSongs(clampLimit(limit)));
    }

    @GetMapping("/songs/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> songDetail(@PathVariable Long id) {
        return songService.getSongById(id)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(s -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("song", s);
                    data.put("related", songService.getRelatedSongs(id, 6));
                    return ResponseEntity.ok(ApiResponse.ok(data));
                })
                .orElse(ApiResponse.notFoundEntity("歌曲不存在"));
    }

    @GetMapping("/songs/search")
    public ApiResponse<List<?>> search(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) return ApiResponse.ok(List.of());
        return ApiResponse.ok(songService.searchSongs(keyword.trim()));
    }

    @GetMapping("/songs/genres")
    public ApiResponse<List<?>> genres() {
        return ApiResponse.ok(songService.getAllGenres());
    }

    @GetMapping("/songs/genre/{genre}")
    public ApiResponse<List<?>> songsByGenre(@PathVariable String genre) {
        return ApiResponse.ok(songService.getSongsByGenre(genre));
    }

    // ==================== 歌单 ====================

    @GetMapping("/playlists")
    public ApiResponse<List<?>> hotPlaylists(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return ApiResponse.ok(playlistService.getHotPlaylists(clampLimit(limit)));
    }

    /** 当前登录用户的歌单列表（JWT 或表单登录均可） */
    @GetMapping("/playlists/mine")
    public ResponseEntity<ApiResponse<List<?>>> myPlaylists(
            @AuthenticationPrincipal User currentUserEntity) {
        // JWT 登录时 principal 是 entity.User，表单登录时是 UserDetails
        // 兼容两种情况：直接用 entity.User 类型
        if (currentUserEntity == null) {
            // 表单登录回退方案：从 UserDetails 取 username
            Object principal = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication().getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.User ud) {
                currentUserEntity = userRepository.findByUsername(ud.getUsername()).orElse(null);
            }
        }
        if (currentUserEntity == null) {
            return ApiResponse.unauthorizedEntity("请先登录");
        }
        return ResponseEntity.ok(ApiResponse.ok(playlistService.getUserPlaylists(currentUserEntity.getId())));
    }

    @GetMapping("/playlists/search")
    public ApiResponse<List<?>> searchPlaylists(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) return ApiResponse.ok(List.of());
        return ApiResponse.ok(playlistService.searchPlaylists(keyword.trim()));
    }

    @GetMapping("/playlists/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> playlistDetail(@PathVariable Long id) {
        return playlistService.getPlaylistById(id)
                .<ResponseEntity<ApiResponse<Map<String, Object>>>>map(p -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("playlist", p);
                    data.put("songs", playlistService.getPlaylistSongs(id));
                    return ResponseEntity.ok(ApiResponse.ok(data));
                })
                .orElse(ApiResponse.notFoundEntity("歌单不存在"));
    }

    @GetMapping("/users/{userId}/playlists")
    public ApiResponse<List<?>> userPlaylists(@PathVariable Long userId) {
        return ApiResponse.ok(playlistService.getUserPlaylists(userId));
    }

    // ==================== 用户交互（点赞/播放计数） ====================

    @PostMapping("/songs/{id}/play")
    public ApiResponse<Map<String, Object>> recordPlay(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Long userId) {
        songService.recordPlay(userId, id);
        return ApiResponse.ok(Map.of("success", true));
    }

    @PostMapping("/songs/{id}/like")
    public ApiResponse<Map<String, Object>> toggleLike(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") Long userId) {
        if (userId <= 0) return ApiResponse.unauthorized("请先登录");
        songService.recordLike(userId, id);
        boolean liked = songService.isLiked(userId, id);
        return ApiResponse.ok(Map.of("liked", liked));
    }

    @GetMapping("/users/{userId}/liked")
    public ApiResponse<List<?>> likedSongs(@PathVariable Long userId) {
        return ApiResponse.ok(songService.getLikedSongs(userId));
    }

    // ==================== 藝人/歌手 ====================

    @GetMapping("/artists/{name}/songs")
    public ApiResponse<List<?>> artistSongs(@PathVariable String name) {
        return ApiResponse.ok(songService.getSongsByArtist(name));
    }

    // ==================== 排行榜 ====================

    @GetMapping("/ranking")
    public ApiResponse<List<?>> ranking(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ApiResponse.ok(songService.getRankingByPlayCount(clampLimit(limit)));
    }

    // ==================== 隨機 ====================

    @GetMapping("/songs/random")
    public ApiResponse<Map<String, Object>> randomSong() {
        Song s = songService.getRandomSong();
        if (s == null) return ApiResponse.notFound("暫無歌曲");
        Map<String, Object> data = new HashMap<>();
        data.put("song", s);
        return ApiResponse.ok(data);
    }

    // ==================== 工具 ====================

    /** 安全的 limit 范围：1 ~ 100，防止数据库爆量或参数非法 */
    private int clampLimit(int limit) {
        return Math.min(Math.max(limit, 1), 100);
    }
}
