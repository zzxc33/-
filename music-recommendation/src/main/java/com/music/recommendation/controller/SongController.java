package com.music.recommendation.controller;

import com.music.recommendation.entity.Comment;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.CommentRepository;
import com.music.recommendation.repository.UserRepository;
import com.music.recommendation.service.SongService;
import com.music.recommendation.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/song")
public class SongController {

    private final SongService songService;
    private final UserService userService;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public SongController(SongService songService, UserService userService,
                          CommentRepository commentRepository, UserRepository userRepository) {
        this.songService = songService;
        this.userService = userService;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    /** 歌曲详情页 */
    @GetMapping("/{id}")
    public String songDetail(@PathVariable Long id,
                             @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
                             Model model) {
        // 歌曲不存在时重定向到搜索页
        Song song = songService.getSongById(id).orElse(null);
        if (song == null) {
            return "redirect:/song/search";
        }
        model.addAttribute("song", song);

        // 记录播放（访客 currentUser 为 null，userId=0 时跳过用户关联）
        Long userId = 0L;
        if (currentUser != null) {
            userId = userService.findByUsername(currentUser.getUsername())
                    .map(User::getId).orElse(0L);
        }
        songService.recordPlay(userId, id);

        // 获取相关歌曲推荐（基于内容）
        model.addAttribute("recommendedSongs", songService.getRelatedSongs(id, 6));

        // 获取真实评论数据
        List<Comment> comments = commentRepository.findBySongIdOrderByCreatedAtDesc(id);
        // 为每个评论填充用户名
        Set<Long> commentUserIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        Map<Long, String> usernameMap = userRepository.findAllById(commentUserIds)
                .stream().collect(Collectors.toMap(User::getId, u ->
                        (u.getNickname() != null && !u.getNickname().isEmpty()) ? u.getNickname() : u.getUsername()));
        comments.forEach(c -> c.setUsername(usernameMap.getOrDefault(c.getUserId(), "匿名用户")));
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", comments.size());

        // 查询当前用户是否已点赞这首歌
        boolean isLiked = userId > 0 && songService.isLiked(userId, id);
        model.addAttribute("isLiked", isLiked);

        return "song-detail";
    }

    /** 搜索页面 */
    @GetMapping("/search")
    public String searchPage(@RequestParam(defaultValue = "") String keyword, Model model) {
        List<Song> results = songService.searchSongs(keyword);
        model.addAttribute("songs", results);
        model.addAttribute("keyword", keyword);
        return "search";
    }

    /** 分类浏览 */
    @GetMapping("/genre/{genre}")
    public String genrePage(@PathVariable String genre, Model model) {
        List<Song> songs = songService.getSongsByGenre(genre);
        model.addAttribute("songs", songs);
        model.addAttribute("currentGenre", genre);
        model.addAttribute("genres", songService.getAllGenres());
        return "genre";
    }

    /** API：搜索歌曲（JSON） */
    @GetMapping("/api/search")
    @ResponseBody
    public Map<String, Object> apiSearch(@RequestParam String keyword) {
        Map<String, Object> result = new HashMap<>();
        result.put("songs", songService.searchSongs(keyword));
        return result;
    }

    /** API：播放并记录 */
    @PostMapping("/api/play/{id}")
    @ResponseBody
    public Map<String, Object> apiPlay(@PathVariable Long id,
                                       @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        Long userId = 0L;
        if (currentUser != null) {
            userId = userService.findByUsername(currentUser.getUsername())
                    .map(User::getId).orElse(0L);
        }
        songService.recordPlay(userId, id);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return result;
    }

    /** API：切换点赞/取消点赞 */
    @PostMapping("/api/like/{id}")
    @ResponseBody
    public Map<String, Object> apiLike(@PathVariable Long id,
                                       @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        Map<String, Object> result = new HashMap<>();
        if (currentUser == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }
        Long userId = userService.findByUsername(currentUser.getUsername())
                .map(User::getId).orElse(0L);
        songService.recordLike(userId, id);

        // 查询当前点赞状态返回给前端
        boolean nowLiked = songService.isLiked(userId, id);

        result.put("success", true);
        result.put("liked", nowLiked);
        // 同时返回更新后的喜欢数
        songService.getSongById(id).ifPresent(song ->
            result.put("likeCount", song.getLikeCount())
        );
        return result;
    }

    /** API：提交评论 */
    @PostMapping("/api/comment")
    @ResponseBody
    public Map<String, Object> apiComment(@RequestParam Long songId,
                                       @RequestParam String content,
                                       @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        Map<String, Object> result = new HashMap<>();
        if (currentUser == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }
        if (content == null || content.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "评论内容不能为空");
            return result;
        }
        if (content.trim().length() > 500) {
            result.put("success", false);
            result.put("message", "评论内容不能超过500字");
            return result;
        }
        if (songId == null) {
            result.put("success", false);
            result.put("message", "歌曲ID不能为空");
            return result;
        }

        Long userId = userService.findByUsername(currentUser.getUsername())
                .map(User::getId).orElse(null);
        if (userId == null) {
            result.put("success", false);
            result.put("message", "用户未登录");
            return result;
        }

        // 检查歌曲是否存在
        if (songService.getSongById(songId).isEmpty()) {
            result.put("success", false);
            result.put("message", "歌曲不存在");
            return result;
        }

        Comment comment = new Comment(songId, userId, content.trim());
        commentRepository.save(comment);

        result.put("success", true);
        result.put("message", "评论发布成功");
        return result;
    }

    /** API：获取歌曲评论列表 */
    @GetMapping("/api/comments/{songId}")
    @ResponseBody
    public List<Map<String, Object>> apiGetComments(@PathVariable Long songId) {
        List<Comment> comments = commentRepository.findBySongIdOrderByCreatedAtDesc(songId);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return comments.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("likeCount", c.getLikeCount());
            m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "");
            // 获取用户名
            String uname = userRepository.findById(c.getUserId())
                    .map(u -> (u.getNickname() != null && !u.getNickname().isEmpty()) ? u.getNickname() : u.getUsername())
                    .orElse("匿名用户");
            m.put("username", uname);
            m.put("userId", c.getUserId());
            return m;
        }).collect(Collectors.toList());
    }
}