package com.music.recommendation.controller;

import com.music.recommendation.entity.Song;
import com.music.recommendation.entity.User;
import com.music.recommendation.repository.SongRepository;
import com.music.recommendation.repository.UserRepository;
import com.music.recommendation.service.SongService;
import com.music.recommendation.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final SongService songService;
    private final SongRepository songRepository;
    private final UserRepository userRepository;

    public AdminController(UserService userService, SongService songService,
                           SongRepository songRepository, UserRepository userRepository) {
        this.userService = userService;
        this.songService = songService;
        this.songRepository = songRepository;
        this.userRepository = userRepository;
    }

    /**
     * 管理员后台首页
     */
    @GetMapping({"", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal User admin, Model model) {
        if (admin == null || !"ROLE_ADMIN".equals(admin.getRole())) {
            return "redirect:/login";
        }

        // 统计数据
        long totalUsers = userRepository.count();
        long totalSongs = songRepository.count();
        Long totalPlayedLong = songRepository.sumTotalPlayCount();
        long totalPlayed = totalPlayedLong != null ? totalPlayedLong : 0;

        model.addAttribute("admin", admin);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("totalSongs", totalSongs);
        model.addAttribute("totalPlayed", totalPlayed);
        return "admin/dashboard";
    }

    /**
     * 歌曲管理列表
     */
    @GetMapping("/songs")
    public String songList(Model model) {
        List<Song> songs = songService.getAllSongs();
        model.addAttribute("songs", songs);
        return "admin/songs";
    }

    /**
     * 用户管理列表
     */
    @GetMapping("/users")
    public String userList(Model model) {
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        return "admin/users";
    }

    /**
     * 推荐引擎诊断页
     */
    @GetMapping("/recommend")
    public String recommendPage(Model model) {
        List<User> users = userRepository.findAll();
        model.addAttribute("users", users);
        return "admin/recommend";
    }

    /**
     * 用户管理 API - 新增/编辑
     */
    @PostMapping("/users/save")
    @ResponseBody
    public Map<String, Object> saveUser(@RequestParam(required = false) Long id,
                                         @RequestParam String username,
                                         @RequestParam(required = false) String password,
                                         @RequestParam(required = false) String nickname,
                                         @RequestParam(required = false) String email,
                                         @RequestParam(required = false) String phone,
                                         @RequestParam(required = false) String role) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            if (id != null) {
                // 编辑用户
                userService.updateUser(id, nickname, email, phone, role, password);
                result.put("success", true);
                result.put("message", "用户已更新");
            } else {
                // 新增用户
                if (userRepository.findByUsername(username).isPresent()) {
                    result.put("success", false);
                    result.put("message", "用户名已存在");
                    return result;
                }
                userService.createUser(username, password, nickname != null ? nickname : username,
                        email, phone, role);
                result.put("success", true);
                result.put("message", "用户已创建");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 删除歌曲
     */
    @PostMapping("/songs/delete/{id}")
    public String deleteSong(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            songRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("message", "歌曲已删除");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/admin/songs";
    }

    /**
     * 删除用户
     */
    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("message", "用户已删除");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * 封禁/解封用户
     */
    @PostMapping("/users/toggle-ban/{id}")
    public String toggleBan(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            String newRole = "ROLE_BANNED".equals(user.getRole()) ? "ROLE_USER" : "ROLE_BANNED";
            userService.updateUser(id, user.getNickname(), user.getEmail(), user.getPhone(), newRole, null);
            String action = "ROLE_BANNED".equals(newRole) ? "已封禁" : "已解封";
            redirectAttributes.addFlashAttribute("message", "用户" + action);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "操作失败：" + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * 歌曲管理 API - 新增/编辑
     */
    @PostMapping("/songs/save")
    @ResponseBody
    public Map<String, Object> saveSong(@RequestParam(required = false) Long id,
                                         @RequestParam String title,
                                         @RequestParam String artist,
                                         @RequestParam(required = false) String album,
                                         @RequestParam(required = false) String genre,
                                         @RequestParam(required = false) String duration,
                                         @RequestParam(required = false) String coverUrl,
                                         @RequestParam(required = false) String description) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            Song song = (id != null) ? songRepository.findById(id).orElse(new Song()) : new Song();
            song.setTitle(title);
            song.setArtist(artist);
            song.setAlbum(album);
            song.setGenre(genre);
            song.setDuration(duration);
            song.setCoverUrl(coverUrl);
            song.setDescription(description);
            if (song.getPlayCount() == null) song.setPlayCount(0L);
            if (song.getLikeCount() == null) song.setLikeCount(0L);
            songRepository.save(song);
            result.put("success", true);
            result.put("message", id != null ? "歌曲已更新" : "歌曲已添加");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败：" + e.getMessage());
        }
        return result;
    }
}