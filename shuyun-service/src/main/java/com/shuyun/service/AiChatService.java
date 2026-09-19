package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.pojo.entity.UserAiChat;

import java.util.List;

/**
 * AI 对话业务接口
 */
public interface AiChatService extends IService<UserAiChat> {

    /**
     * 处理一次对话：保存用户消息 → 生成回复 → 保存 AI 回复
     *
     * @param userId    用户ID
     * @param message   用户消息
     * @param sessionId 会话ID
     * @return AI 回复内容
     */
    String chat(Long userId, String message, String sessionId);

    /**
     * 查询某会话的全部对话记录
     *
     * @param sessionId 会话ID
     * @return 对话记录
     */
    List<UserAiChat> listBySession(String sessionId);
}
