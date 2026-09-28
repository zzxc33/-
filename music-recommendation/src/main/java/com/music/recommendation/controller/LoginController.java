package com.music.recommendation.controller;

import com.music.recommendation.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 跳转到登录页面
     */
    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "switch", required = false) String switchAccount,
            Model model) {

        if (error != null) {
            model.addAttribute("error", "用户名或密码错误！");
        }
        if (logout != null) {
            model.addAttribute("message", "您已成功退出登录！");
        }
        if (switchAccount != null) {
            model.addAttribute("message", "已退出当前账号，请使用其他账号登录");
            model.addAttribute("isSwitch", true);
        }
        return "login";
    }

    /**
     * 切换账号：退出当前账号并跳转到登录页
     */
    @GetMapping("/switch-account")
    public String switchAccount(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login?switch=true";
    }

    /**
     * 跳转到注册页面
     */
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    /**
     * 处理注册请求
     */
    @PostMapping("/doRegister")
    @ResponseBody
    public Map<String, Object> doRegister(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String email,
            HttpSession session) {

        Map<String, Object> result = new HashMap<>();
        try {
            // 验证用户协议
            if (username == null || username.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "用户名不能为空");
                return result;
            }
            if (password == null || password.length() < 6) {
                result.put("success", false);
                result.put("message", "密码至少需要6位");
                return result;
            }

            boolean success = userService.register(username.trim(), password, email);
            if (success) {
                result.put("success", true);
                result.put("message", "注册成功");
            } else {
                result.put("success", false);
                result.put("message", "用户名已存在");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "注册失败：" + e.getMessage());
        }
        return result;
    }
}