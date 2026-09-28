package com.music.recommendation.service;

import com.music.recommendation.entity.User;
import com.music.recommendation.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 用户注册 */
    @Override
    public boolean register(String username, String password, String email) {
        if (userRepository.existsByUsername(username)) {
            return false;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setNickname(username);
        userRepository.save(user);
        return true;
    }

    /** 根据用户名查找用户 */
    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /** 根据ID查找用户 */
    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /** 验证密码 */
    @Override
    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    /** 创建用户（管理员） */
    @Override
    public User createUser(String username, String password, String nickname, String email, String phone, String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setRole(role != null && !role.isEmpty() ? role : "ROLE_USER");
        return userRepository.save(user);
    }

    /** 更新用户（管理员） */
    @Override
    public User updateUser(Long id, String nickname, String email, String phone, String role, String password) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if (nickname != null) user.setNickname(nickname);
        if (email != null) user.setEmail(email);
        if (phone != null) user.setPhone(phone);
        if (role != null && !role.isEmpty()) user.setRole(role);
        if (password != null && !password.isEmpty()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        return userRepository.save(user);
    }

    /** 删除用户 */
    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /** 获取所有用户 */
    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}
