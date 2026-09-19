package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.mapper.UserAiChatMapper;
import com.shuyun.pojo.entity.UserAiChat;
import com.shuyun.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AI 对话业务实现
 *
 * <p>当前返回占位回复，后续替换为女娲智能体的 SSE 流式接口。</p>
 */
@Slf4j
@Service
public class AiChatServiceImpl extends ServiceImpl<UserAiChatMapper, UserAiChat> implements AiChatService {

    /** 角色：用户 */
    private static final String ROLE_USER = "user";

    /** 角色：AI 助手 */
    private static final String ROLE_ASSISTANT = "assistant";

    /** 占位回复（待替换为女娲智能体 SSE 流式输出） */
    private static final String PLACEHOLDER_REPLY = "我是蜀韵·智游管家，正在学习中……";

    @Override
    public String chat(Long userId, String message, String sessionId) {
        saveMessage(userId, sessionId, ROLE_USER, message);
        saveMessage(userId, sessionId, ROLE_ASSISTANT, PLACEHOLDER_REPLY);
        log.info("AI 对话：userId={}, sessionId={}, messageLen={}", userId, sessionId,
                message == null ? 0 : message.length());
        return PLACEHOLDER_REPLY;
    }

    @Override
    public List<UserAiChat> listBySession(String sessionId) {
        return list(Wrappers.<UserAiChat>lambdaQuery()
                .eq(UserAiChat::getSessionId, sessionId)
                .orderByAsc(UserAiChat::getId));
    }

    /**
     * 保存一条对话记录
     */
    private void saveMessage(Long userId, String sessionId, String role, String content) {
        UserAiChat chat = new UserAiChat();
        chat.setUserId(userId);
        chat.setSessionId(sessionId);
        chat.setRole(role);
        chat.setContent(content);
        chat.setTokens(0);
        save(chat);
    }
}
