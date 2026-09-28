package com.music.recommendation.service;

import com.music.recommendation.entity.User;
import com.music.recommendation.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库查询用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 返回 Spring Security 的 UserDetails 对象
        // 使用数据库中实际的角色，而不是硬编码 ROLE_USER
        String role = user.getRole();
        if (role == null || role.isEmpty()) {
            role = "ROLE_USER";
        }

        // 检查是否被封禁
        boolean isBanned = "ROLE_BANNED".equals(role);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                !isBanned, // enabled - 被封禁用户不可登录
                true,      // accountNonExpired
                true,      // credentialsNonExpired
                !isBanned, // accountNonLocked - 被封禁用户锁定
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }
}