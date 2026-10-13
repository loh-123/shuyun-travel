package com.shuyun.ai.nuwa;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 女娲智能体平台配置
 *
 * <p>读取 application.yml 中 {@code nuwa.*} 配置项：</p>
 *
 * <pre>
 * nuwa:
 *   mode: ${NUWA_MODE:mock}                       # mock 或 real
 *   api-key: ${NUWA_API_KEY:mock-key}
 *   base-url: ${NUWA_BASE_URL:mock}
 *   agent-id: ${NUWA_AGENT_ID:mock-agent}
 *   conversation-add-path: /api/v1/chat/conversation/add
 *   chat-path: /api/v1/chat                        # 实际请求 {chat-path}/{conversationId}
 * </pre>
 *
 * <p>{@code mode} 决定启用哪个客户端实现：{@code mock} → {@link NuwaMockClient}，
 * {@code real} → {@link NuwaRealClient}。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "nuwa")
public class NuwaConfig {

    /** 运行模式：mock-模拟流式（默认），real-接入女娲真实接口 */
    private String mode = "mock";

    /** 平台 API Key（建议通过环境变量 NUWA_API_KEY 注入） */
    private String apiKey = "mock-key";

    /** 平台基础地址 */
    private String baseUrl = "mock";

    /** 智能体ID */
    private String agentId = "mock-agent";

    /** 请求超时时间（毫秒） */
    private long timeout = 60000L;

    /** 发起会话接口路径（POST 后返回 conversationId） */
    private String conversationAddPath = "/api/v1/chat/conversation/add";

    /** 发起会话（流式）接口路径前缀，实际请求为 {chatPath}/{conversationId} */
    private String chatPath = "/api/v1/chat";

    /** 是否启用工具调用（Function Calling） */
    private boolean toolsEnabled = false;

    /** 调用失败重试次数 */
    private int retryCount = 2;

    /** 单用户每分钟最大对话次数 */
    private int rateLimitPerMinute = 10;

    /** 模拟模式下的占位 key */
    private static final String MOCK_KEY = "mock-key";

    /** 模拟模式下的占位地址 */
    private static final String MOCK_BASE_URL = "mock";

    /**
     * 是否为真实接口模式
     *
     * @return true-接入女娲真实接口
     */
    public boolean isRealMode() {
        return "real".equalsIgnoreCase(mode);
    }

    /**
     * 判断真实接口所需凭证是否已配置完整
     *
     * @return true-已配置可用的真实凭证
     */
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey)
                && !MOCK_KEY.equals(apiKey)
                && StringUtils.hasText(baseUrl)
                && !MOCK_BASE_URL.equals(baseUrl);
    }
}
