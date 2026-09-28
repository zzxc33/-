package com.music.recommendation.config;

import com.music.recommendation.controller.AuthApiController;
import com.music.recommendation.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 小程序 JWT Token 认证过滤器
 *
 * 工作流程：
 *   1. 从请求头 Authorization: Bearer xxx 提取 Token
 *   2. 通过 AuthApiController.resolveUser() 验证 Token 有效性和 TTL
 *   3. 成功 → 构建 Authentication 注入 Spring Security 上下文
 *   4. 失败 / 无 Token → 放行（让后续 SecurityConfig 的 permitAll 或鉴权逻辑决定）
 *
 * 注意：/api/auth/login 和 /api/auth/register 在 SecurityConfig 中已 permitAll，
 *      但为了 Token 过滤器能为已登录用户注入上下文，我们不过滤这两个端点。
 *      其他 /api/** 端点如果需要强制登录，在 Controller 层再校验。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            User user = AuthApiController.resolveUser(authHeader);

            if (user != null) {
                // 构建 Spring Security Authentication
                String role = user.getRole() != null ? user.getRole() : "ROLE_USER";
                List<SimpleGrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority(role));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                user,          // principal — 直接传 User 对象
                                null,          // credentials — 无密码
                                authorities
                        );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 注入 Security 上下文
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            // user == null 说明 Token 无效或过期，不注入，继续走 filter chain
            // 后续如果是 permitAll 端点正常访问，如果是需要鉴权的端点会被 SecurityConfig 拦截
        }

        filterChain.doFilter(request, response);
    }
}
