package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.entity.User;
import com.shuyun.pojo.entity.UserViewHistory;

import java.util.List;

/**
 * 用户业务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册（需校验邮箱验证码）
     *
     * @param username 用户名
     * @param email    邮箱
     * @param password 明文密码
     * @param code     邮箱验证码
     * @return 注册成功的用户
     */
    User register(String username, String email, String password, String code);

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

    /**
     * 后台用户分页查询
     *
     * @param page      页码，从 1 开始
     * @param size      每页大小
     * @param keyword   关键词（匹配用户名 / 邮箱，可选）
     * @param status    状态：0-禁用，1-正常；不传为全部
     * @param role      角色：USER / ADMIN；不传为全部
     * @param startDate 注册开始日期（yyyy-MM-dd，可选）
     * @param endDate   注册结束日期（yyyy-MM-dd，可选）
     * @return 分页结果（已抹除密码）
     */
    PageResult<User> pageUsers(int page, int size, String keyword, Integer status, String role,
                               String startDate, String endDate);

    /**
     * 导出用的用户列表（不分页，条件同 {@link #pageUsers}）
     *
     * @param keyword   关键词（可选）
     * @param status    状态（可选）
     * @param role      角色（可选）
     * @param startDate 注册开始日期（可选）
     * @param endDate   注册结束日期（可选）
     * @return 用户列表（已抹除密码）
     */
    List<User> listUsersForExport(String keyword, Integer status, String role, String startDate, String endDate);

    /**
     * 修改用户状态（启用 / 禁用）
     *
     * @param id     用户ID
     * @param status 目标状态：0-禁用，1-正常
     */
    void updateStatus(Long id, Integer status);

    /**
     * 重置用户密码为默认密码
     *
     * @param id 用户ID
     * @return 重置后的明文密码
     */
    String resetPassword(Long id);

    /**
     * 软删除用户
     *
     * @param id 用户ID
     */
    void removeUser(Long id);

    /**
     * 最近行为记录（来自浏览足迹）
     *
     * @param userId 用户ID
     * @param limit  条数
     * @return 浏览记录列表（按浏览时间倒序）
     */
    List<UserViewHistory> recentBehaviors(Long userId, int limit);
}
