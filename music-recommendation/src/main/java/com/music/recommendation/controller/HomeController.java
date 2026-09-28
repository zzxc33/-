package com.music.recommendation.controller;

import com.music.recommendation.entity.Playlist;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.PlaylistRepository;
import com.music.recommendation.service.SongService;
import com.music.recommendation.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    private final SongService songService;
    private final UserService userService;
    private final PlaylistRepository playlistRepository;

    public HomeController(SongService songService, UserService userService, PlaylistRepository playlistRepository) {
        this.songService = songService;
        this.userService = userService;
        this.playlistRepository = playlistRepository;
    }

    @GetMapping("/index")
    public String index(@AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
                        Model model) {
        // 兼容未登录用户：currentUser 为 null 时走冷启动推荐
        User user = null;
        Long userId = 0L;
        String username = "访客";
        String nickname = "音乐爱好者";
        boolean isAdmin = false;
        boolean isLoggedIn = currentUser != null;

        if (isLoggedIn) {
            user = userService.findByUsername(currentUser.getUsername()).orElse(null);
            if (user != null) {
                userId = user.getId();
                username = currentUser.getUsername();
                nickname = user.getNickname() != null ? user.getNickname() : currentUser.getUsername();
                isAdmin = "ROLE_ADMIN".equals(user.getRole());
            }
        }

        // 获取推荐歌曲（混合推荐引擎）
        List<Song> recommendedSongs = songService.getRecommendedSongs(userId, 12);
        // 获取热门歌曲
        List<Song> hotSongs = songService.getHotSongs(8);
        // 获取最新歌曲
        List<Song> newSongs = songService.getNewSongs(8);
        // 获取所有风格
        List<String> genres = songService.getAllGenres();

        // 统计卡数据（未登录时都是 0）
        long totalPlayed = songService.countTotalPlayed(userId);
        long totalLiked = songService.countTotalLiked(userId);
        List<Playlist> userPlaylists = userId > 0 ? playlistRepository.findByUserId(userId) : List.of();
        int playlistCount = userPlaylists.size();

        model.addAttribute("recommendedSongs", recommendedSongs);
        model.addAttribute("hotSongs", hotSongs);
        model.addAttribute("newSongs", newSongs);
        model.addAttribute("genres", genres);
        model.addAttribute("username", username);
        model.addAttribute("nickname", nickname);
        model.addAttribute("totalPlayed", totalPlayed);
        model.addAttribute("totalLiked", totalLiked);
        model.addAttribute("playlistCount", playlistCount);
        model.addAttribute("likedSongs", songService.getLikedSongs(userId));
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isLoggedIn", isLoggedIn);

        return "index";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/index";
    }
}