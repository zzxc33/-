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

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 專題頁面：每日推薦、排行榜
 */
@Controller
public class RecommendController {

    private final SongService songService;
    private final UserService userService;
    private final PlaylistRepository playlistRepository;

    public RecommendController(SongService songService,
                               UserService userService,
                               PlaylistRepository playlistRepository) {
        this.songService = songService;
        this.userService = userService;
        this.playlistRepository = playlistRepository;
    }

    // ==================== 每日推薦專題頁 ====================

    /**
     * 「為你每日推薦」專題頁
     * - 未登錄：冷啟動推薦（熱門 50% + 隨機 50%）
     * - 已登錄：混合推薦引擎個性化結果
     */
    @GetMapping("/recommend/daily")
    public String dailyRecommend(@AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
                                  Model model) {
        User user = null;
        Long userId = 0L;
        boolean isLoggedIn = currentUser != null;

        if (isLoggedIn) {
            user = userService.findByUsername(currentUser.getUsername()).orElse(null);
            if (user != null) userId = user.getId();
        }

        // 今天的日期（yyyy年M月d日 星期X）
        String today = java.time.LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日"));
        java.time.DayOfWeek weekdayDay = java.time.LocalDate.now().getDayOfWeek();
        String weekday;
        switch (weekdayDay) {
            case MONDAY: weekday = "星期一"; break;
            case TUESDAY: weekday = "星期二"; break;
            case WEDNESDAY: weekday = "星期三"; break;
            case THURSDAY: weekday = "星期四"; break;
            case FRIDAY: weekday = "星期五"; break;
            case SATURDAY: weekday = "星期六"; break;
            case SUNDAY: weekday = "星期日"; break;
            default: weekday = ""; break;
        }

        // 混合推薦引擎：18 首
        List<Song> dailySongs = songService.getRecommendedSongs(userId, 18);
        // 熱門歌單展示
        List<Playlist> hotPlaylists = playlistRepository.findAll().stream().limit(4).toList();
        // 今日熱門 Top 5
        List<Song> hotTop5 = songService.getHotSongs(5);

        model.addAttribute("today", today);
        model.addAttribute("weekday", weekday);
        model.addAttribute("greeting", isLoggedIn ? buildGreeting(user) : buildGuestGreeting());
        model.addAttribute("dailySongs", dailySongs);
        model.addAttribute("hotPlaylists", hotPlaylists);
        model.addAttribute("hotTop5", hotTop5);
        model.addAttribute("isLoggedIn", isLoggedIn);
        model.addAttribute("nickname", isLoggedIn && user != null ? user.getNickname() : "訪客");

        return "recommend-daily";
    }

    // ==================== 播放量排行榜 ====================

    /**
     * 播放量 Top 100 排行榜
     */
    @GetMapping("/ranking")
    public String ranking(Model model) {
        List<Song> top100 = songService.getRankingByPlayCount(100);

        // 按風格統計前 100 的分佈（用於側邊欄顯示）
        Map<String, Long> genreStat = new java.util.LinkedHashMap<>();
        for (Song s : top100) {
            String g = s.getGenre() != null ? s.getGenre() : "其他";
            genreStat.merge(g, 1L, Long::sum);
        }

        model.addAttribute("top100", top100);
        model.addAttribute("genreStat", genreStat);
        model.addAttribute("totalCount", songService.getAllGenres().size());

        return "ranking";
    }

    // ==================== 輔助 ====================

    private String buildGreeting(User user) {
        String nick = user != null && user.getNickname() != null ? user.getNickname() : "朋友";
        String timeGreet = timeGreeting();
        return timeGreet + "，" + nick + "！今日為你精心挑選了 18 首歌";
    }

    private String buildGuestGreeting() {
        String timeGreet = timeGreeting();
        return timeGreet + "！登錄後獲得專屬個性化推薦";
    }

    /** 根據小時返回問候語 */
    private static String timeGreeting() {
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 0 && hour <= 5) return "夜深了";
        if (hour >= 6 && hour <= 11) return "早安";
        if (hour >= 12 && hour <= 13) return "午好";
        if (hour >= 14 && hour <= 17) return "下午好";
        if (hour >= 18 && hour <= 20) return "晚上好";
        if (hour >= 21 && hour <= 23) return "夜深了";
        return "你好";
    }
}
