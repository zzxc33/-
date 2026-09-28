package com.music.recommendation.service;

import com.music.recommendation.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务接口
 */
public interface UserService {

    /** 用户注册 */
    boolean register(String username, String password, String email);

    /** 根据用户名查找用户 */
    Optional<User> findByUsername(String username);

    /** 根据ID查找用户 */
    Optional<User> findById(Long id);

    /** 验证密码 */
    boolean checkPassword(String rawPassword, String encodedPassword);

    /** 创建用户（管理员） */
    User createUser(String username, String password, String nickname, String email, String phone, String role);

    /** 更新用户（管理员） */
    User updateUser(Long id, String nickname, String email, String phone, String role, String password);

    /** 删除用户 */
    void deleteUser(Long id);

    /** 获取所有用户 */
    List<User> getAllUsers();
}
