package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.EmailSubscriptionMapper;
import com.shuyun.mapper.UserFeedbackMapper;
import com.shuyun.mapper.UserNoteMapper;
import com.shuyun.mapper.UserNotificationMapper;
import com.shuyun.pojo.entity.EmailSubscription;
import com.shuyun.pojo.entity.UserFeedback;
import com.shuyun.pojo.entity.UserNote;
import com.shuyun.pojo.entity.UserNotification;
import com.shuyun.pojo.vo.AdminNoteVO;
import com.shuyun.pojo.vo.FeedbackVO;
import com.shuyun.service.AdminAuditService;
import com.shuyun.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台内容审核业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditServiceImpl implements AdminAuditService {

    /** 笔记审核状态：待审核 */
    private static final int NOTE_PENDING = 0;

    /** 笔记审核状态：已通过 */
    private static final int NOTE_APPROVED = 1;

    /** 笔记审核状态：已拒绝 */
    private static final int NOTE_REJECTED = 2;

    /** 反馈状态：待处理 */
    private static final int FEEDBACK_PENDING = 0;

    /** 反馈状态：已处理 */
    private static final int FEEDBACK_DONE = 1;

    private final UserNoteMapper userNoteMapper;

    private final UserFeedbackMapper userFeedbackMapper;

    private final EmailSubscriptionMapper emailSubscriptionMapper;

    private final UserNotificationMapper userNotificationMapper;

    private final UserService userService;

    @Override
    public Map<String, Object> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pendingNotes", userNoteMapper.selectCount(Wrappers.<UserNote>lambdaQuery()
                .eq(UserNote::getDeleted, 0).eq(UserNote::getAuditStatus, NOTE_PENDING)));
        data.put("pendingFeedback", userFeedbackMapper.selectCount(Wrappers.<UserFeedback>lambdaQuery()
                .eq(UserFeedback::getDeleted, 0).eq(UserFeedback::getStatus, FEEDBACK_PENDING)));
        data.put("subscriptions", emailSubscriptionMapper.selectCount(Wrappers.<EmailSubscription>lambdaQuery()
                .eq(EmailSubscription::getDeleted, 0)));
        // 已处理总数 = 已处理反馈 + 已审核（通过 / 拒绝）的笔记
        long doneFeedback = userFeedbackMapper.selectCount(Wrappers.<UserFeedback>lambdaQuery()
                .eq(UserFeedback::getDeleted, 0).eq(UserFeedback::getStatus, FEEDBACK_DONE));
        long auditedNotes = userNoteMapper.selectCount(Wrappers.<UserNote>lambdaQuery()
                .eq(UserNote::getDeleted, 0).in(UserNote::getAuditStatus, NOTE_APPROVED, NOTE_REJECTED));
        data.put("processed", doneFeedback + auditedNotes);
        return data;
    }

    @Override
    public PageResult<AdminNoteVO> pageNotes(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<UserNote> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(UserNote::getDeleted, 0)
                .eq(status != null, UserNote::getAuditStatus, status);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(UserNote::getTitle, keyword).or().like(UserNote::getContent, keyword));
        }
        // 待审核优先，其次按时间倒序
        wrapper.orderByAsc(UserNote::getAuditStatus).orderByDesc(UserNote::getCreateTime);

        Page<UserNote> result = userNoteMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserNote> records = result.getRecords();

        Map<Long, String> nameMap = userNameMap(records.stream().map(UserNote::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        List<AdminNoteVO> vos = new ArrayList<>(records.size());
        for (UserNote note : records) {
            AdminNoteVO vo = new AdminNoteVO();
            BeanUtils.copyProperties(note, vo);
            vo.setUsername(nameMap.getOrDefault(note.getUserId(), "未知用户"));
            vos.add(vo);
        }
        return PageResult.of(result.getTotal(), vos, page, size);
    }

    @Override
    public void approveNote(Long id) {
        UserNote exist = userNoteMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("笔记不存在");
        }
        userNoteMapper.update(null, Wrappers.<UserNote>lambdaUpdate()
                .set(UserNote::getAuditStatus, NOTE_APPROVED)
                .set(UserNote::getRejectReason, null)
                .eq(UserNote::getId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectNote(Long id, String reason) {
        UserNote exist = userNoteMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("笔记不存在");
        }
        userNoteMapper.update(null, Wrappers.<UserNote>lambdaUpdate()
                .set(UserNote::getAuditStatus, NOTE_REJECTED)
                .set(UserNote::getRejectReason, reason)
                .eq(UserNote::getId, id));
        // 发送站内信通知作者
        sendNotification(exist.getUserId(), "笔记审核未通过",
                "你的笔记「" + (StringUtils.hasText(exist.getTitle()) ? exist.getTitle() : "无标题") + "」未通过审核。理由："
                        + (StringUtils.hasText(reason) ? reason : "未填写"));
    }

    @Override
    public void removeNote(Long id) {
        userNoteMapper.update(null, Wrappers.<UserNote>lambdaUpdate()
                .set(UserNote::getDeleted, 1)
                .eq(UserNote::getId, id));
    }

    @Override
    public PageResult<FeedbackVO> pageFeedback(Integer status, String keyword, int page, int size) {
        LambdaQueryWrapper<UserFeedback> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(UserFeedback::getDeleted, 0)
                .eq(status != null, UserFeedback::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(UserFeedback::getContent, keyword);
        }
        wrapper.orderByAsc(UserFeedback::getStatus).orderByDesc(UserFeedback::getCreateTime);

        Page<UserFeedback> result = userFeedbackMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserFeedback> records = result.getRecords();

        Map<Long, String> nameMap = userNameMap(records.stream().map(UserFeedback::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
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
    @Transactional(rollbackFor = Exception.class)
    public void handleFeedback(Long id, Integer status, String reply, boolean notifyEmail) {
        UserFeedback exist = userFeedbackMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("反馈不存在");
        }
        userFeedbackMapper.update(null, Wrappers.<UserFeedback>lambdaUpdate()
                .set(UserFeedback::getStatus, status == null ? FEEDBACK_DONE : status)
                .set(UserFeedback::getReply, reply)
                .eq(UserFeedback::getId, id));

        if (notifyEmail) {
            // 收件地址优先取反馈留的联系方式，其次取用户邮箱
            String to = StringUtils.hasText(exist.getContact()) ? exist.getContact() : null;
            if (to == null && exist.getUserId() != null) {
                var user = userService.getById(exist.getUserId());
                to = user == null ? null : user.getEmail();
            }
            sendEmail(to, "【蜀韵·智游】您的反馈已收到回复", reply);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replyFeedback(Long id, String reply) {
        UserFeedback exist = userFeedbackMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("反馈不存在");
        }
        userFeedbackMapper.update(null, Wrappers.<UserFeedback>lambdaUpdate()
                .set(UserFeedback::getStatus, FEEDBACK_DONE)
                .set(UserFeedback::getReply, reply)
                .eq(UserFeedback::getId, id));
        // 发送邮件（收件地址优先联系方式，其次用户邮箱）
        String to = StringUtils.hasText(exist.getContact()) ? exist.getContact() : null;
        if (to == null && exist.getUserId() != null) {
            var user = userService.getById(exist.getUserId());
            to = user == null ? null : user.getEmail();
        }
        sendEmail(to, "【蜀韵·智游】您的反馈已收到回复", reply);
    }

    @Override
    public void removeFeedback(Long id) {
        userFeedbackMapper.update(null, Wrappers.<UserFeedback>lambdaUpdate()
                .set(UserFeedback::getDeleted, 1)
                .eq(UserFeedback::getId, id));
    }

    @Override
    public PageResult<EmailSubscription> pageSubscriptions(String keyword, int page, int size) {
        LambdaQueryWrapper<EmailSubscription> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(EmailSubscription::getDeleted, 0)
                .like(StringUtils.hasText(keyword), EmailSubscription::getEmail, keyword)
                .orderByDesc(EmailSubscription::getCreateTime);
        Page<EmailSubscription> result = emailSubscriptionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public List<EmailSubscription> listAllSubscriptions() {
        return emailSubscriptionMapper.selectList(Wrappers.<EmailSubscription>lambdaQuery()
                .eq(EmailSubscription::getDeleted, 0)
                .orderByDesc(EmailSubscription::getCreateTime));
    }

    @Override
    public void removeSubscription(Long id) {
        emailSubscriptionMapper.update(null, Wrappers.<EmailSubscription>lambdaUpdate()
                .set(EmailSubscription::getDeleted, 1)
                .eq(EmailSubscription::getId, id));
    }

    /**
     * 批量取「用户ID -> 用户名」
     */
    private Map<Long, String> userNameMap(Set<Long> userIds) {
        Map<Long, String> map = new HashMap<>();
        if (userIds != null && !userIds.isEmpty()) {
            userService.listByIds(userIds).forEach(u -> map.put(u.getId(), u.getUsername()));
        }
        return map;
    }

    /**
     * 发送站内信
     *
     * @param userId  接收用户ID
     * @param title   标题
     * @param content 内容
     */
    private void sendNotification(Long userId, String title, String content) {
        if (userId == null) {
            return;
        }
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setType("NOTE_AUDIT");
        notification.setIsRead(0);
        notification.setDeleted(0);
        userNotificationMapper.insert(notification);
        log.info("发送站内信 -> userId={}, title={}", userId, title);
    }

    /**
     * 发送邮件（占位实现）
     *
     * <p>当前仅打印日志；接入真实 SMTP 后可替换为 JavaMailSender 发送。</p>
     *
     * @param to      收件人
     * @param subject 主题
     * @param content 正文
     */
    private void sendEmail(String to, String subject, String content) {
        if (!StringUtils.hasText(to)) {
            log.warn("反馈回复未发送邮件：收件地址为空");
            return;
        }
        log.info("发送反馈回复邮件 -> to={}, subject={}, content={}", to, subject, content);
    }
}
