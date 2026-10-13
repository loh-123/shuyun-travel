package com.shuyun.ai.nuwa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuyun.common.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 女娲智能体真实客户端（会话式协议）
 *
 * <p>在 {@code nuwa.mode=real} 下启用。女娲平台的开放 API 是「会话式」的，调用分两步：</p>
 * <ol>
 *   <li>建会话：{@code POST {conversationAddPath}}，体 {@code {"agentId":<数字>}}，
 *       返回 {@code data} 即 conversationId（数字）；</li>
 *   <li>发消息：{@code POST {chatPath}/{conversationId}}，必须带
 *       {@code Accept: text/event-stream}，体 {@code {"message":"...","conversationId":<数字>}}，
 *       返回 SSE 流；正文增量在事件 {@code data.data.text}（eventType=MESSAGE / role=ASSISTANT）。</li>
 * </ol>
 *
 * <p>本地 sessionId → 女娲 conversationId 的映射缓存在 Redis（{@code nuwa:conv:{userId}:{sessionId}}），
 * 缺失时自动建会话，从而在同一会话内保留多轮上下文（女娲服务端按 conversationId 维护上下文）。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nuwa.mode", havingValue = "real")
public class NuwaRealClient implements NuwaClient {

    /** 兜底文案（连接失败且无任何内容时） */
    private static final String FALLBACK = "管家正在打盹，请稍后再试～";

    /** 本地会话映射在 Redis 中的 key 前缀 */
    private static final String CONV_KEY_PREFIX = "nuwa:conv:";

    /** 会话映射缓存时长（天） */
    private static final long CONV_TTL_DAYS = 7L;

    /** 女娲事件类型：正文消息 */
    private static final String EVENT_MESSAGE = "MESSAGE";

    /** 女娲消息子类型：思考过程（推理） */
    private static final String TYPE_THINK = "THINK";

    /** 女娲消息子类型：正文回答 */
    private static final String TYPE_CHAT = "CHAT";

    /** 女娲事件类型：错误（会话未在执行等） */
    private static final String EVENT_ERROR = "ERROR";

    /** 角色：AI 助手（只转发助手正文，忽略用户回显） */
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    private final NuwaConfig nuwaConfig;

    private final RedisUtil redisUtil;

    private final ObjectMapper objectMapper;

    /** 预构建的 WebClient（复用连接池） */
    private final WebClient webClient;

    public NuwaRealClient(NuwaConfig nuwaConfig, RedisUtil redisUtil, ObjectMapper objectMapper) {
        this.nuwaConfig = nuwaConfig;
        this.redisUtil = redisUtil;
        this.objectMapper = objectMapper;

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(nuwaConfig.getTimeout()));
        this.webClient = WebClient.builder()
                .baseUrl(nuwaConfig.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + nullToEmpty(nuwaConfig.getApiKey()))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    @Override
    public Flux<NuwaChunk> chatStream(String message, String sessionId, Long userId) {
        if (!nuwaConfig.isConfigured()) {
            log.warn("女娲真实接口未配置 api-key / base-url，跳过真实调用");
            return Flux.just(NuwaChunk.text(FALLBACK));
        }

        // 建/取会话 → 发消息（阻塞式 Redis 与建会话调用统一放到 boundedElastic 线程执行）
        return Mono.fromCallable(() -> resolveConversationId(userId, sessionId))
                .flatMapMany(convId -> sendMessage(convId, message))
                .onErrorResume(e -> {
                    log.error("女娲调用最终失败：{}", e.getMessage());
                    return Flux.just(NuwaChunk.text(FALLBACK));
                });
    }

    /**
     * 取或新建「本地会话 → 女娲 conversationId」的映射
     *
     * @param userId    用户ID（匿名会话用 anon）
     * @param sessionId 本地会话ID
     * @return 女娲 conversationId
     */
    private long resolveConversationId(Long userId, String sessionId) {
        String key = CONV_KEY_PREFIX + (userId == null ? "anon" : userId) + ":" + sessionId;
        // 读缓存（Redis 不可用时降级：本次新建会话，不中断对话）
        try {
            String cached = redisUtil.get(key);
            if (StringUtils.hasText(cached)) {
                return Long.parseLong(cached.trim());
            }
        } catch (Exception e) {
            log.warn("读取女娲会话映射失败（Redis 不可用？），本次新建会话：{}", e.getMessage());
        }
        long convId = createConversation();
        try {
            redisUtil.set(key, String.valueOf(convId), CONV_TTL_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            // Redis 不可用不影响本次对话，只是无法复用会话
            log.warn("写入女娲会话映射失败：{}", e.getMessage());
        }
        return convId;
    }

    /**
     * 建会话：{@code POST {conversationAddPath}}，返回的 {@code data} 即 conversationId
     *
     * @return 女娲 conversationId
     */
    private long createConversation() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentId", agentIdValue());

        String resp = webClient.post()
                .uri(nuwaConfig.getConversationAddPath())
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(nuwaConfig.getTimeout()));
        try {
            JsonNode data = objectMapper.readTree(resp).path("data");
            if (data.isNumber() || data.isTextual()) {
                return data.asLong();
            }
        } catch (Exception e) {
            log.warn("解析女娲建会话响应失败：{}", resp);
        }
        throw new IllegalStateException("女娲建会话失败：" + resp);
    }

    /**
     * 发消息（返回 SSE 流）：{@code POST {chatPath}/{conversationId}}，正文增量在 {@code data.data.text}
     *
     * @param conversationId 女娲会话ID
     * @param message        用户消息
     * @return 正文分片流
     */
    private Flux<NuwaChunk> sendMessage(long conversationId, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("conversationId", conversationId);

        return webClient.post()
                .uri(nuwaConfig.getChatPath() + "/" + conversationId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
                })
                .mapNotNull(this::parseEvent)
                .filter(chunk -> chunk.hasContent() || chunk.hasReasoning());
    }

    /**
     * 解析单个 SSE 事件：仅取 eventType=MESSAGE 且 role=ASSISTANT 的 {@code data.data.text}
     *
     * @param event SSE 事件
     * @return 正文分片；无可提取内容时返回 {@code null}
     */
    private NuwaChunk parseEvent(ServerSentEvent<String> event) {
        String payload = event.data();
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.path("eventType").asText("");
            if (EVENT_ERROR.equals(eventType)) {
                log.warn("女娲事件错误：{}", root.path("error").asText(""));
                return null;
            }
            if (!EVENT_MESSAGE.equals(eventType)) {
                return null;
            }
            JsonNode data = root.path("data");
            if (!ROLE_ASSISTANT.equalsIgnoreCase(data.path("role").asText(""))) {
                return null;
            }
            String text = data.path("text").asText("");
            if (text.isEmpty()) {
                return null;
            }
            // 按 type 区分：THINK=思考过程（推理），CHAT=正文回答
            String type = data.path("type").asText("");
            return TYPE_THINK.equalsIgnoreCase(type) ? NuwaChunk.reasoning(text) : NuwaChunk.text(text);
        } catch (Exception e) {
            log.debug("解析女娲事件失败，已忽略：{}", payload);
            return null;
        }
    }

    /**
     * agentId 取值：数字串转为 Long（接口按数字校验），否则原样返回
     */
    private Object agentIdValue() {
        String id = nuwaConfig.getAgentId();
        if (StringUtils.hasText(id) && id.matches("\\d+")) {
            try {
                return Long.parseLong(id);
            } catch (NumberFormatException ignored) {
                // 超长则退回字符串
            }
        }
        return id;
    }

    /**
     * null 安全的字符串转换
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
