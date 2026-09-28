package com.music.recommendation.controller;

import com.music.recommendation.entity.Playlist;
import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.PlaylistRepository;
import com.music.recommendation.repository.UserRepository;
import com.music.recommendation.service.SongService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final SongService songService;
    private final PasswordEncoder passwordEncoder;

    public ProfileController(UserRepository userRepository, PlaylistRepository playlistRepository,
                             SongService songService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.playlistRepository = playlistRepository;
        this.songService = songService;
        this.passwordEncoder = passwordEncoder;
    }

    /** 个人中心页面 */
    @GetMapping
    public String profilePage(@AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
                              Model model) {
        User user = userRepository.findByUsername(currentUser.getUsername()).orElse(null);
        if (user != null) {
            model.addAttribute("user", user);
            Long userId = user.getId();

            long totalPlayed = songService.countTotalPlayed(userId);
            long totalLiked = songService.countTotalLiked(userId);
            List<Playlist> userPlaylists = playlistRepository.findByUserId(userId);
            int playlistCount = userPlaylists.size();
            List<Song> likedSongs = songService.getLikedSongs(userId);
            Map<String, Long> genreStats = songService.getUserGenreStats(userId);

            model.addAttribute("totalPlayed", totalPlayed);
            model.addAttribute("totalLiked", totalLiked);
            model.addAttribute("playlistCount", playlistCount);
            model.addAttribute("likedSongs", likedSongs);
            model.addAttribute("genreStats", genreStats);
            model.addAttribute("displayName",
                    (user.getNickname() != null && !user.getNickname().isEmpty()) ? user.getNickname() : user.getUsername());
        }
        return "profile";
    }

    /** API：更新个人资料 */
    @PostMapping("/api/update")
    @ResponseBody
    public Map<String, Object> apiUpdate(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String favoriteGenre) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findByUsername(currentUser.getUsername()).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户未登录");
            return result;
        }
        if (nickname != null) user.setNickname(nickname);
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        if (favoriteGenre != null) user.setFavoriteGenre(favoriteGenre);
        userRepository.save(user);
        result.put("success", true);
        result.put("message", "更新成功");
        return result;
    }

    /** API：修改密码 */
    @PostMapping("/api/changePassword")
    @ResponseBody
    public Map<String, Object> apiChangePassword(
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User currentUser,
            @RequestParam String oldPassword,
            @RequestParam String newPassword) {
        Map<String, Object> result = new HashMap<>();
        User user = userRepository.findByUsername(currentUser.getUsername()).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户未登录");
            return result;
        }
        // 验证旧密码（BCrypt匹配）
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            result.put("success", false);
            result.put("message", "旧密码错误");
            return result;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        result.put("success", true);
        result.put("message", "密码修改成功");
        return result;
    }
}