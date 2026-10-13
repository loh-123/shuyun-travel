package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.UserFeedbackMapper;
import com.shuyun.pojo.entity.UserFeedback;
import com.shuyun.pojo.vo.FeedbackVO;
import com.shuyun.service.FeedbackService;
import com.shuyun.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 问题反馈业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl extends ServiceImpl<UserFeedbackMapper, UserFeedback> implements FeedbackService {

    /** 状态：待处理 */
    private static final int STATUS_PENDING = 0;

    /** 状态：已处理 */
    private static final int STATUS_DONE = 1;

    private final UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long userId, String type, String content, String contact) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("反馈内容不能为空");
        }

        UserFeedback feedback = new UserFeedback();
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setContent(content);
        feedback.setContact(contact);
        feedback.setStatus(STATUS_PENDING);
        save(feedback);

        log.info("收到问题反馈：id={}, userId={}, type={}", feedback.getId(), userId, type);
        return feedback.getId();
    }

    @Override
    public PageResult<UserFeedback> pageMine(Long userId, int page, int size) {
        Page<UserFeedback> result = page(new Page<>(page, size),
                Wrappers.<UserFeedback>lambdaQuery()
                        .eq(UserFeedback::getUserId, userId)
                        .orderByDesc(UserFeedback::getCreateTime));
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public PageResult<FeedbackVO> pageAll(Integer status, String keyword, int page, int size) {
        LambdaQueryWrapper<UserFeedback> wrapper = Wrappers.lambdaQuery();
        if (status != null) {
            wrapper.eq(UserFeedback::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(UserFeedback::getContent, keyword);
        }
        // 待处理优先，其次按时间倒序
        wrapper.orderByAsc(UserFeedback::getStatus).orderByDesc(UserFeedback::getCreateTime);

        Page<UserFeedback> result = page(new Page<>(page, size), wrapper);
        List<UserFeedback> records = result.getRecords();

        // 批量取用户名，避免 N+1
        Map<Long, String> nameMap = new HashMap<>();
        Set<Long> userIds = records.stream()
                .map(UserFeedback::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            userService.listByIds(userIds).forEach(u -> nameMap.put(u.getId(), u.getUsername()));
        }

        List<FeedbackVO> vos = new ArrayList<>(records.size());
        for (UserFeedback feedback : records) {
            FeedbackVO vo = new FeedbackVO();
            BeanUtils.copyProperties(feedback, vo);
            vo.setUsername(nameMap.getOrDefault(feedback.getUserId(), "未知用户"));
            vos.add(vo);
        }
        return PageResult.of(result.getTotal(), vos, page, size);
    }

    @Override
    public void handle(Long id, Integer status, String reply) {
        UserFeedback exist = getById(id);
        if (exist == null) {
            throw new BusinessException("反馈不存在");
        }

        UserFeedback update = new UserFeedback();
        update.setId(id);
        update.setStatus(status == null ? STATUS_DONE : status);
        update.setReply(reply);
        updateById(update);

        log.info("处理问题反馈：id={}, status={}", id, update.getStatus());
    }
}
