package com.music.recommendation.controller;

import com.music.recommendation.common.ApiResponse;
import com.music.recommendation.entity.User;
import com.music.recommendation.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 小程序认证 API（简化版 Token 方案）
 * 内存存储 Token → User 映射，适合毕设演示
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final UserService userService;

    /** Token 存储（生产环境应该用 Redis，毕设够用） */
    private static final Map<String, User> TOKENS = new ConcurrentHashMap<>();
    /** Token 有效期：7 天（毫秒） */
    private static final long TOKEN_TTL = 7L * 24 * 60 * 60 * 1000;
    /** Token 创建时间 */
    private static final Map<String, Long> TOKEN_CREATED = new ConcurrentHashMap<>();

    public AuthApiController(UserService userService) {
        this.userService = userService;
    }

    /** 注册 */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String email = body.get("email");

        if (username == null || username.length() < 2)
            return ApiResponse.badRequest("用户名至少 2 个字符");
        if (password == null || password.length() < 6)
            return ApiResponse.badRequest("密码至少 6 位");

        boolean ok = userService.register(username, password, email);
        if (!ok) return ApiResponse.error(409, "用户名已存在");

        // 注册成功自动登录
        return login(body);
    }

    /** 登录 */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null)
            return ApiResponse.badRequest("用户名和密码不能为空");

        User user = userService.findByUsername(username).orElse(null);
        if (user == null || !userService.checkPassword(password, user.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }

        // 生成 Token
        String token = UUID.randomUUID().toString().replace("-", "");
        TOKENS.put(token, user);
        TOKEN_CREATED.put(token, System.currentTimeMillis());

        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("user", toPublicUser(user));
        return ApiResponse.ok(data);
    }

    /** 登出 */
    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            TOKENS.remove(token);
            TOKEN_CREATED.remove(token);
        }
        return ApiResponse.ok(Map.of("success", true));
    }

    /** 根据 Token 获取当前用户信息 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        User user = resolveUser(auth);
        if (user == null) return ApiResponse.unauthorized("未登录或 Token 已过期");
        return ApiResponse.ok(toPublicUser(user));
    }

    /** 工具方法：从 Authorization 头解析用户 */
    public static User resolveUser(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        String token = auth.substring(7);
        Long created = TOKEN_CREATED.get(token);
        if (created == null) return null;
        if (System.currentTimeMillis() - created > TOKEN_TTL) {
            TOKENS.remove(token);
            TOKEN_CREATED.remove(token);
            return null;
        }
        return TOKENS.get(token);
    }

    /** 脱敏后的用户信息（不返回密码） */
    private Map<String, Object> toPublicUser(User user) {
        Map<String, Object> u = new HashMap<>();
        u.put("id", user.getId());
        u.put("username", user.getUsername());
        u.put("nickname", user.getNickname());
        u.put("email", user.getEmail());
        u.put("phone", user.getPhone());
        u.put("role", user.getRole());
        u.put("avatarUrl", user.getAvatarUrl());
        return u;
    }
}
