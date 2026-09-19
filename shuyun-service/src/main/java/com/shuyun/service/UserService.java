package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.pojo.entity.User;

/**
 * 用户业务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param username 用户名
     * @param email    邮箱
     * @param password 明文密码
     * @return 注册成功的用户
     */
    User register(String username, String email, String password);

    /**
     * 用户登录，支持用户名或邮箱
     *
     * @param account  用户名或邮箱
     * @param password 明文密码
     * @return JWT token
     */
    String login(String account, String password);

    /**
     * 获取当前登录用户
     *
     * @param userId 用户ID
     * @return 用户
     */
    User getCurrentUser(Long userId);

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 原密码（明文）
     * @param newPassword 新密码（明文）
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 修改资料（用户名 / 邮箱 / 一句话简介，均为可选）
     *
     * @param userId   用户ID
     * @param username 新用户名（空则不改）
     * @param email    新邮箱（空则不改）
     * @param bio      新简介（null 则不改）
     * @return 修改后的用户（不含密码）
     */
    User updateProfile(Long userId, String username, String email, String bio);

    /**
     * 更新头像地址
     *
     * @param userId    用户ID
     * @param avatarUrl 头像可访问地址
     */
    void updateAvatar(Long userId, String avatarUrl);
}
