package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.JwtUtil;
import com.shuyun.mapper.UserMapper;
import com.shuyun.pojo.entity.User;
import com.shuyun.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /** 新用户默认角色 */
    private static final String DEFAULT_ROLE = "USER";

    /** 新用户默认状态：1-正常 */
    private static final Integer DEFAULT_STATUS = 1;

    private final JwtUtil jwtUtil;

    /** 密码加密器 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public User register(String username, String email, String password) {
        if (count(Wrappers.<User>lambdaQuery().eq(User::getUsername, username)) > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (count(Wrappers.<User>lambdaQuery().eq(User::getEmail, email)) > 0) {
            throw new BusinessException("邮箱已被注册");
        }

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
}
