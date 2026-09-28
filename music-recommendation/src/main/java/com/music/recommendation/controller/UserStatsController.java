package com.music.recommendation.controller;

import com.music.recommendation.entity.User;
import com.music.recommendation.service.SongService;
import com.music.recommendation.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

/**
 * 用戶行為分析頁面（我的品味）
 */
@Controller
@RequestMapping("/profile/stats")
public class UserStatsController {

    private final SongService songService;
    private final UserService userService;

    public UserStatsController(SongService songService, UserService userService) {
        this.songService = songService;
        this.userService = userService;
    }

    @GetMapping
    public String userStats(@AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
                            Model model) {
        if (currentUser == null) {
            return "redirect:/login";
        }
        User user = userService.findByUsername(currentUser.getUsername()).orElse(null);
        if (user == null) return "redirect:/login";
        Long userId = user.getId();

        // 1. 最常聽的風格（餅圖）
        Map<String, Long> genreStats = songService.getUserGenreStats(userId);
        // 2. 最愛歌手榜（柱狀圖）
        Map<String, Long> topArtists = songService.getUserTopArtists(userId);
        // 3. 7天播放熱力圖數據
        List<int[]> heatmapData = songService.getPlayHeatmapData(userId);
        // 4. 最近播放
        var recentPlays = songService.getUserRecentPlays(userId, 10);
        // 5. 總體統計
        long totalPlayed = songService.countTotalPlayed(userId);
        long totalLiked = songService.countTotalLiked(userId);

        model.addAttribute("genreStats", genreStats);
        model.addAttribute("topArtists", topArtists);
        model.addAttribute("heatmapData", heatmapData);
        model.addAttribute("recentPlays", recentPlays);
        model.addAttribute("totalPlayed", totalPlayed);
        model.addAttribute("totalLiked", totalLiked);
        model.addAttribute("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());

        return "user-stats";
    }
}
