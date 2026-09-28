package com.music.recommendation.controller;

import com.music.recommendation.entity.Playlist;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.UserRepository;
import com.music.recommendation.service.PlaylistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/playlist")
public class PlaylistController {

    private final PlaylistService playlistService;
    private final UserRepository userRepository;

    public PlaylistController(PlaylistService playlistService, UserRepository userRepository) {
        this.playlistService = playlistService;
        this.userRepository = userRepository;
    }

    /** 我的歌单页 */
    @GetMapping("/mine")
    public String myPlaylists(@AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
                              Model model) {
        User user = userRepository.findByUsername(currentUser.getUsername()).orElse(null);
        if (user != null) {
            List<Playlist> playlists = playlistService.getUserPlaylists(user.getId());
            model.addAttribute("playlists", playlists);
        }
        return "my-playlists";
    }

    /** 歌单详情页 */
    @GetMapping("/{id}")
    public String playlistDetail(@PathVariable Long id, Model model) {
        playlistService.getPlaylistById(id).ifPresent(playlist -> {
            model.addAttribute("playlist", playlist);
            List<Song> songs = playlistService.getPlaylistSongs(id);
            model.addAttribute("songs", songs);
        });
        return "playlist-detail";
    }

    /** 新建歌单页面 */
    @GetMapping("/create")
    public String createPlaylistPage() {
        return "create-playlist";
    }

    /** API：新建歌单 */
    @PostMapping("/api/create")
    @ResponseBody
    public Map<String, Object> apiCreate(@RequestParam String name,
                                         @RequestParam(required = false) String description,
                                         @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findByUsername(currentUser.getUsername()).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户未登录");
            return result;
        }
        Playlist playlist = playlistService.createPlaylist(name, description, user);
        result.put("success", true);
        result.put("playlistId", playlist.getId());
        return result;
    }

    /** API：添加歌曲到歌单 */
    @PostMapping("/api/addSong")
    @ResponseBody
    public Map<String, Object> apiAddSong(@RequestParam Long playlistId,
                                          @RequestParam Long songId) {
        Map<String, Object> result = new HashMap<>();
        boolean success = playlistService.addSongToPlaylist(playlistId, songId);
        result.put("success", success);
        result.put("message", success ? "已添加到歌单" : "添加失败，歌曲可能已在歌单中");
        return result;
    }

    /** API：从歌单移除歌曲 */
    @PostMapping("/api/removeSong")
    @ResponseBody
    public Map<String, Object> apiRemoveSong(@RequestParam Long playlistId,
                                             @RequestParam Long songId) {
        Map<String, Object> result = new HashMap<>();
        playlistService.removeSongFromPlaylist(playlistId, songId);
        result.put("success", true);
        return result;
    }

    /** API：删除歌单 */
    @PostMapping("/api/delete")
    @ResponseBody
    public Map<String, Object> apiDelete(@RequestParam Long id) {
        Map<String, Object> result = new HashMap<>();
        playlistService.deletePlaylist(id);
        result.put("success", true);
        return result;
    }
}