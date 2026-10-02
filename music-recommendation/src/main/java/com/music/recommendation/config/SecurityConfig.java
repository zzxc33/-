package com.music.recommendation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS 跨域配置：允许小程序开发工具、H5、本地前端 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // CORS 优先
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // CSRF：/api/** 用 JWT Token 无状态鉴权，/doLogin 是未登录用户表单提交也跳过 CSRF
                //   /song/api/** 是 Thymeleaf 页面 fetch() 调用的 JSON 接口（play/like/comment），
                //   前端不发 _csrf token，必须跳过否则被 403 重定向到登录页
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/doLogin", "/song/api/**")
                )

                // JWT Token 过滤器（放在 UsernamePasswordAuthenticationFilter 之前）
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // 配置请求授权
                .authorizeHttpRequests(auth -> auth
                        // Swagger / OpenAPI / Druid监控 - 全部放行
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/actuator/**",
                                "/druid/**"
                        ).permitAll()
                        // 小程序 REST API - 分级鉴权
                        //   GET/HEAD/OPTIONS：公开只读（首页、歌曲列表、搜索、推荐、排行榜）
                        //   POST/PUT/DELETE：需要认证（播放记录、点赞、评论、创建歌单、更新资料）
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        // 注册/登录/登出显式放行
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/register").permitAll()
                        // 其余 POST/PUT/DELETE（播放记录、点赞、评论、歌单 CRUD、用户资料更新）需要认证
                        .requestMatchers(HttpMethod.POST, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/**").authenticated()
                        // 注册接口 - 明确允许 POST 请求
                        .requestMatchers(HttpMethod.POST, "/doRegister").permitAll()
                        // 管理后台 - 仅管理员可访问
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 登录页面、静态资源、验证码接口允许所有人访问
                        .requestMatchers(
                                "/",
                                "/index",
                                "/login",
                                "/register",
                                "/switch-account",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/captcha",
                                "/song/**",
                                "/genre/**"
                        ).permitAll()
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )

                // 配置表单登录
                .formLogin(form -> form
                        // 自定义登录页面
                        .loginPage("/login")
                        // 登录处理接口
                        .loginProcessingUrl("/doLogin")
                        // 登录成功跳转
                        .defaultSuccessUrl("/index", true)
                        // 登录失败跳转
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // 配置登出
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )

                // 记住我功能
                .rememberMe(remember -> remember
                        .rememberMeParameter("rememberMe")
                        .tokenValiditySeconds(7 * 24 * 60 * 60) // 7天
                )

                // 异常处理：无权限访问 /admin 时跳转到首页
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.sendRedirect("/index?accessDenied=true");
                        })
                );

        return http.build();
    }
}