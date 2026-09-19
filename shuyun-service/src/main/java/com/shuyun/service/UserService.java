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
}
