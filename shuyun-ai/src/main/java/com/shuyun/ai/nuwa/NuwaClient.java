package com.shuyun.ai.nuwa;

import reactor.core.publisher.Flux;

/**
 * 女娲智能体客户端接口
 *
 * <p>屏蔽底层实现差异：{@link NuwaMockClient} 返回模拟流式数据，
 * {@link NuwaRealClient} 调用女娲平台真实接口。由 {@code nuwa.mode} 决定装配哪个实现。</p>
 */
public interface NuwaClient {

    /**
     * 发起一次流式对话
     *
     * @param message   用户消息
     * @param sessionId 会话ID
     * @param userId    用户ID（用于构建上下文）
     * @return 分片流（每片可能含正文 / 工具调用 / token 用量）
     */
    Flux<NuwaChunk> chatStream(String message, String sessionId, Long userId);
}
