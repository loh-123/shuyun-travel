package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.pojo.entity.UserAiChat;

import java.util.List;
import java.util.Map;

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
     * 查询某会话的全部对话记录（限定归属用户）
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 对话记录（按时间正序）
     */
    List<UserAiChat> listBySession(Long userId, String sessionId);

    /**
     * 查询当前用户的会话列表（按最近消息倒序）
     *
     * @param userId 用户ID
     * @return [{sessionId, title, lastTime, messageCount}]
     */
    List<Map<String, Object>> listSessions(Long userId);

    /**
     * 删除某会话的全部消息
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     */
    void removeSession(Long userId, String sessionId);
}
