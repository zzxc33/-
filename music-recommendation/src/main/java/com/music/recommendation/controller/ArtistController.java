package com.music.recommendation.controller;

import com.music.recommendation.entity.Song;
import com.music.recommendation.service.SongService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 藝人/歌手頁面
 */
@Controller
public class ArtistController {

    private final SongService songService;

    public ArtistController(SongService songService) {
        this.songService = songService;
    }

    /**
     * 歌手頁面
     * - 列出該歌手所有歌曲（按播放量排序）
     * - 顯示歌手相關資訊（模擬）
     */
    @GetMapping("/artist/{name}")
    public String artistDetail(@PathVariable String name,
                               @RequestParam(required = false, defaultValue = "1") int page,
                               Model model) {
        String artistName = name.replace("-", " "); // URL 友好轉換：周杰倫 -> 周杰倫（但我們用 %2F 或 _ 處理）
        List<Song> allSongs = songService.getSongsByArtist(artistName);

        if (allSongs.isEmpty()) {
            // 嘗試模糊搜索
            List<Song> fuzzy = songService.searchSongs(artistName);
            model.addAttribute("artist", artistName);
            model.addAttribute("songs", fuzzy);
            model.addAttribute("found", !fuzzy.isEmpty());
            // 構建藝人簡介（模擬）
            model.addAttribute("bio", buildArtistBio(artistName));
            model.addAttribute("totalPlays", fuzzy.stream().mapToLong(s -> s.getPlayCount() != null ? s.getPlayCount() : 0).sum());
            return "artist";
        }

        // 簡單分頁：每頁 15 首
        int pageSize = 15;
        int totalPages = Math.max(1, (allSongs.size() + pageSize - 1) / pageSize);
        int safePage = Math.min(Math.max(page, 1), totalPages);
        int fromIdx = (safePage - 1) * pageSize;
        int toIdx = Math.min(fromIdx + pageSize, allSongs.size());
        List<Song> pageSongs = allSongs.subList(fromIdx, toIdx);

        // 統計
        long totalPlays = allSongs.stream().mapToLong(s -> s.getPlayCount() != null ? s.getPlayCount() : 0).sum();
        long totalLikes = allSongs.stream().mapToLong(s -> s.getLikeCount() != null ? s.getLikeCount() : 0).sum();
        String topGenre = allSongs.stream()
                .filter(s -> s.getGenre() != null)
                .collect(java.util.stream.Collectors.groupingBy(Song::getGenre, java.util.stream.Collectors.counting()))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey).orElse("流行");

        model.addAttribute("artist", artistName);
        model.addAttribute("songs", pageSongs);
        model.addAttribute("found", true);
        model.addAttribute("totalSongs", allSongs.size());
        model.addAttribute("totalPlays", totalPlays);
        model.addAttribute("totalLikes", totalLikes);
        model.addAttribute("topGenre", topGenre);
        model.addAttribute("bio", buildArtistBio(artistName));
        model.addAttribute("currentPage", safePage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasNext", safePage < totalPages);
        model.addAttribute("hasPrev", safePage > 1);

        return "artist";
    }

    /** 模擬藝人簡介（根據歌手名生成） */
    private String buildArtistBio(String name) {
        // 為已知歌手提供真實簡介
        return switch (name) {
            case "周杰伦" -> "周杰伦（Jay Chou），1979年出生於台灣省新北市，華語樂壇天王級歌手、詞曲創作人、電影導演。自 2000 年出道以來，先後發行《范特西》《葉惠美》《魔傑座》等 15 張專輯，其音樂融合流行、嘻哈、R&B、中國風等多種風格，被譽為「華語流行樂之王」。";
            case "五月天" -> "五月天（Mayday），台灣搖滾樂團，成立於 1997 年。由主唱阿信、團長吉他手怪獸、吉他手石頭、貝斯手瑪莎、鼓手冠佑組成。代表作包括《倔強》《知足》《突然好想你》等，是華語樂壇最具影響力的樂團之一。";
            case "Queen" -> "Queen（皇后樂隊），英國搖滾樂團，成立於 1970 年。由主唱 Freddie Mercury、吉他手 Brian May、貝斯手 John Deacon、鼓手 Roger Taylor 組成。代表作《Bohemian Rhapsody》《We Will Rock You》等，被譽為歷史上最偉大的搖滾樂團之一。";
            case "Beyond" -> "Beyond，香港搖滾樂團，成立於 1983 年。由黃家駒、黃家強、黃貫中、葉世榮組成。代表作《光輝歲月》《真的愛你》《海闊天空》等，是華語搖滾的開拓者。";
            case "李宗盛" -> "李宗盛，1958 年出生於台灣省台北市，華語樂壇資深音樂人、創作歌手、唱片製作人。代表作《凡人歌》《真心英雄》《山丘》等，被譽為「華語樂壇教父」。";
            default -> name + "，當代優秀音樂創作者，擅長多種音樂風格，作品深受廣大樂迷喜愛。";
        };
    }
}
