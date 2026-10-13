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
     * 保存一条对话记录
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param role      角色：user / assistant
     * @param content   消息内容
     */
    void saveMessage(Long userId, String sessionId, String role, String content);

    /**
     * 保存 AI 回复并记录本次调用的观测指标
     *
     * @param userId     用户ID
     * @param sessionId  会话ID
     * @param content    AI 回复内容
     * @param tokens     token 用量（可为 null）
     * @param inputLen   输入长度（字符数）
     * @param outputLen  输出长度（字符数）
     * @param durationMs 调用耗时（毫秒）
     * @param success    是否成功
     */
    void saveAiReply(Long userId, String sessionId, String content, Integer tokens,
                     Integer inputLen, Integer outputLen, Long durationMs, boolean success);

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

    /**
     * 查询当前用户的 AI 画像（兴趣 / 同行 / 预算等），供对话页「记忆体」面板展示
     *
     * @param userId 用户ID
     * @return 画像字段（无记录时返回空 Map）
     */
    Map<String, Object> profile(Long userId);
}
