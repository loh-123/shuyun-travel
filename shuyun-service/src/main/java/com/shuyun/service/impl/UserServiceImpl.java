package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.JwtUtil;
import com.shuyun.common.PageResult;
import com.shuyun.common.RedisUtil;
import com.shuyun.mapper.UserMapper;
import com.shuyun.mapper.UserViewHistoryMapper;
import com.shuyun.pojo.entity.User;
import com.shuyun.pojo.entity.UserViewHistory;
import com.shuyun.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** Redis 中邮箱验证码 key 前缀 */
    private static final String EMAIL_CODE_KEY_PREFIX = "email:code:";

    /** 新用户默认角色 */
    private static final String DEFAULT_ROLE = "USER";

    /** 新用户默认状态：1-正常 */
    private static final Integer DEFAULT_STATUS = 1;

    /** 后台重置密码后的默认密码 */
    private static final String DEFAULT_PASSWORD = "123456";

    private final JwtUtil jwtUtil;

    private final UserViewHistoryMapper userViewHistoryMapper;

    private final RedisUtil redisUtil;

    /** 密码加密器 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User register(String username, String email, String password, String code) {
        // 1. 校验邮箱验证码（存于 Redis，50 秒有效）
        String cachedCode = redisUtil.get(EMAIL_CODE_KEY_PREFIX + email);
        if (cachedCode == null || !cachedCode.equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }

        if (count(Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (count(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)) > 0) {
            throw new BusinessException("邮箱已被注册");
        }

        // 2. 校验通过后立即失效，防止验证码重复使用
        redisUtil.delete(EMAIL_CODE_KEY_PREFIX + email);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(DEFAULT_ROLE);
        user.setStatus(DEFAULT_STATUS);
        save(user);

        log.info("用户注册成功：id={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    @Override
    public String login(String account, String password) {
        User user = getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, account)
                .or()
                .eq(User::getEmail, account), false);

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }

        // 记录最后登录时间（SQL 原子更新，避免覆盖其他字段）
        update(Wrappers.<User>lambdaUpdate()
                .setSql("last_login_time = NOW()")
                .eq(User::getId, user.getId()));

        log.info("用户登录成功：id={}, username={}", user.getId(), user.getUsername());
        return jwtUtil.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public User getCurrentUser(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        updateById(update);

        log.info("用户修改密码：id={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(Long userId, String username, String email, String bio) {
        User user = getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 用户名：非空且变化时才校验唯一性（排除自己）
        if (StringUtils.hasText(username) && !username.equals(user.getUsername())) {
            if (count(Wrappers.<User>lambdaQuery()
                    .eq(User::getUsername, username)
                    .ne(User::getId, userId)) > 0) {
                throw new BusinessException("用户名已存在");
            }
            user.setUsername(username);
        }

        // 邮箱：同上
        if (StringUtils.hasText(email) && !email.equals(user.getEmail())) {
            if (count(Wrappers.<User>lambdaQuery()
                    .eq(User::getEmail, email)
                    .ne(User::getId, userId)) > 0) {
                throw new BusinessException("邮箱已被使用");
            }
            user.setEmail(email);
        }

        if (bio != null) {
            user.setBio(bio);
        }

        updateById(user);
        // 返回前抹掉密码
        user.setPassword(null);
        log.info("用户修改资料：id={}", userId);
        return user;
    }

    @Override
    public void updateAvatar(Long userId, String avatarUrl) {
        User update = new User();
        update.setId(userId);
        update.setAvatar(avatarUrl);
        updateById(update);
        log.info("用户更新头像：id={}, url={}", userId, avatarUrl);
    }

    @Override
    public PageResult<User> pageUsers(int page, int size, String keyword, Integer status, String role,
                                     String startDate, String endDate) {
        Page<User> result = page(new Page<>(page, size), buildUserWrapper(keyword, status, role, startDate, endDate));
        // 抹除密码，避免泄露
        result.getRecords().forEach(u -> u.setPassword(null));
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public List<User> listUsersForExport(String keyword, Integer status, String role, String startDate, String endDate) {
        List<User> list = list(buildUserWrapper(keyword, status, role, startDate, endDate));
        list.forEach(u -> u.setPassword(null));
        return list;
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        User update = new User();
        update.setId(id);
        update.setStatus(status);
        updateById(update);
        log.info("后台修改用户状态：id={}, status={}", id, status);
    }

    @Override
    public String resetPassword(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        User update = new User();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        updateById(update);
        log.info("后台重置用户密码：id={}", id);
        return DEFAULT_PASSWORD;
    }

    @Override
    public void removeUser(Long id) {
        User user = getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 逻辑删除：仅置 deleted=1，保留数据（后台查询会过滤已删除记录）
        update(Wrappers.<User>lambdaUpdate().set(User::getDeleted, 1).eq(User::getId, id));
        log.info("后台删除用户（软删除）：id={}", id);
    }

    @Override
    public List<UserViewHistory> recentBehaviors(Long userId, int limit) {
        return userViewHistoryMapper.selectList(Wrappers.<UserViewHistory>lambdaQuery()
                .eq(UserViewHistory::getUserId, userId)
                .orderByDesc(UserViewHistory::getViewTime)
                .last("limit " + limit));
    }

    /**
     * 构建后台用户查询条件（关键词 / 状态 / 角色 / 注册日期，均过滤已删除）
     *
     * @param keyword   关键词（匹配用户名 / 邮箱）
     * @param status    状态
     * @param role      角色
     * @param startDate 注册开始日期（yyyy-MM-dd，可选）
     * @param endDate   注册结束日期（yyyy-MM-dd，可选）
     * @return 查询条件
     */
    private LambdaQueryWrapper<User> buildUserWrapper(String keyword, Integer status, String role,
                                                      String startDate, String endDate) {
        LambdaQueryWrapper<User> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(User::getDeleted, 0)
                .eq(status != null, User::getStatus, status)
                .eq(StringUtils.hasText(role), User::getRole, role);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword).or().like(User::getEmail, keyword));
        }
        // 注册时间范围（含首尾当天）
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(User::getCreateTime, startDate + " 00:00:00");
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(User::getCreateTime, endDate + " 23:59:59");
        }
        wrapper.orderByDesc(User::getCreateTime);
        return wrapper;
    }
}
