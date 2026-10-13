package com.shuyun.ai.nuwa;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shuyun.ai.tool.NuwaToolService;
import com.shuyun.mapper.UserProfileMapper;
import com.shuyun.pojo.entity.User;
import com.shuyun.pojo.entity.UserAiChat;
import com.shuyun.pojo.entity.UserProfile;
import com.shuyun.pojo.vo.UserTripVO;
import com.shuyun.service.AiChatService;
import com.shuyun.service.TripService;
import com.shuyun.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 女娲消息体构建器
 *
 * <p>负责把「系统提示词（含用户画像） + 历史对话 + 用户上下文」组装成发送给女娲平台的消息数组，
 * 并按需附带工具（Function Calling）声明。</p>
 */
@Component
@RequiredArgsConstructor
public class NuwaMessageBuilder {

    /** 系统提示词：蜀韵·智游管家的人设与职责边界 */
    private static final String SYSTEM_PROMPT = """
            你是蜀韵·智游管家，一名专业的四川文化旅行顾问。
            你精通川剧、蜀绣、川菜、茶马古道、三国文化、三星堆、大熊猫、酒文化等巴蜀文化。
            你能为用户规划四川行程、推荐景点美食、提供实时天气关怀、讲解文化知识。
            回答要简洁专业，带一点四川人特有的热情和幽默。
            涉及具体地点时给出实际可用的建议。
            如果用户问非四川相关问题，礼貌引导回四川旅行主题。
            """;

    /** 角色：系统 */
    private static final String ROLE_SYSTEM = "system";

    /** 角色：用户 */
    private static final String ROLE_USER = "user";

    /** 角色：AI 助手 */
    private static final String ROLE_ASSISTANT = "assistant";

    /** 最多携带的历史消息条数（最近 10 条，防止上下文过长） */
    private static final int HISTORY_LIMIT = 10;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NuwaConfig nuwaConfig;

    private final AiChatService aiChatService;

    private final UserService userService;

    private final TripService tripService;

    private final UserProfileMapper userProfileMapper;

    private final NuwaToolService nuwaToolService;

    /**
     * 构建发送给女娲平台的完整请求体
     *
     * @param message   本轮用户消息
     * @param sessionId 会话ID
     * @param userId    用户ID（用于取历史对话与用户上下文）
     * @return 请求体 {agent_id, model, stream, messages, tools?}
     */
    public Map<String, Object> buildRequestBody(String message, String sessionId, Long userId) {
        List<Map<String, Object>> messages = new ArrayList<>();
        // 1) 系统提示词（附带用户画像与当前时间）
        messages.add(messageOf(ROLE_SYSTEM, buildSystemPrompt(userId)));
        // 2) 历史对话 + 本轮消息
        messages.addAll(buildHistory(message, sessionId, userId));

        Map<String, Object> body = new LinkedHashMap<>();
        // 女娲平台以 agent_id 指定智能体；同时给出 model 以兼容 OpenAI 风格网关
        body.put("agent_id", nuwaConfig.getAgentId());
        body.put("model", nuwaConfig.getAgentId());
        body.put("stream", true);
        body.put("messages", messages);
        // 3) 工具声明（可选，需平台支持 OpenAI tools 协议）
        if (nuwaConfig.isToolsEnabled()) {
            body.put("tools", nuwaToolService.schemas());
            body.put("tool_choice", "auto");
        }
        return body;
    }

    /**
     * 拼接系统提示词与用户画像
     *
     * <p>画像优先从 {@code user_profile} 表读取；缺失时用该用户「最近一条行程」
     * （兴趣偏好 / 同行类型 / 预算范围）补齐。</p>
     */
    private String buildSystemPrompt(Long userId) {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
        String username = "旅行者";
        String age = "未设置";
        String gender = "未设置";
        String region = "未设置";
        String interests = "未设置";
        String companion = "未设置";
        String budget = "未设置";

        User user = userId == null ? null : userService.getById(userId);
        if (user != null && StringUtils.hasText(user.getUsername())) {
            username = user.getUsername();
        }

        // 1) 优先读取用户画像表
        boolean profiled = false;
        if (userId != null) {
            UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                    .eq(UserProfile::getUserId, userId)
                    .eq(UserProfile::getDeleted, 0)
                    .last("limit 1"));
            if (profile != null) {
                profiled = true;
                if (StringUtils.hasText(profile.getAgeRange())) {
                    age = profile.getAgeRange();
                }
                if (StringUtils.hasText(profile.getGender())) {
                    gender = profile.getGender();
                }
                if (StringUtils.hasText(profile.getRegion())) {
                    region = profile.getRegion();
                }
                if (StringUtils.hasText(profile.getInterests())) {
                    interests = profile.getInterests();
                }
                if (StringUtils.hasText(profile.getCompanion())) {
                    companion = profile.getCompanion();
                }
                if (StringUtils.hasText(profile.getBudget())) {
                    budget = profile.getBudget();
                }
            }
        }

        // 2) 画像缺失时，用最近一条行程补齐
        if (!profiled && userId != null) {
            List<UserTripVO> trips = tripService.listMyTrips(userId);
            if (trips != null && !trips.isEmpty()) {
                UserTripVO latest = trips.get(0);
                if (StringUtils.hasText(latest.getInterests())) {
                    interests = latest.getInterests();
                }
                if (StringUtils.hasText(latest.getCompanion())) {
                    companion = latest.getCompanion();
                }
                if (latest.getBudget() != null) {
                    budget = latest.getBudget().toPlainString() + " 元";
                }
            }
        }

        sb.append("\n\n【当前用户信息】\n");
        sb.append("用户名：").append(username).append("\n");
        sb.append("年龄段：").append(age).append("\n");
        sb.append("性别：").append(gender).append("\n");
        sb.append("常驻地域：").append(region).append("\n");
        sb.append("兴趣偏好：").append(interests).append("\n");
        sb.append("同行类型：").append(companion).append("\n");
        sb.append("预算范围：").append(budget).append("\n");
        sb.append("当前时间：").append(TIME_LABEL.format(LocalDateTime.now())).append("\n");
        sb.append("提示：若用户未说明城市，请先礼貌询问。");
        return sb.toString();
    }

    /**
     * 构建历史消息数组（已按时间正序），并确保包含本轮用户消息
     */
    private List<Map<String, Object>> buildHistory(String message, String sessionId, Long userId) {
        List<UserAiChat> history = userId == null
                ? new ArrayList<>()
                : aiChatService.listBySession(userId, sessionId);

        // 仅取最近 HISTORY_LIMIT 条，避免上下文过长
        int from = Math.max(0, history.size() - HISTORY_LIMIT);
        List<UserAiChat> recent = history.subList(from, history.size());

        List<Map<String, Object>> messages = new ArrayList<>();
        for (UserAiChat chat : recent) {
            if (!StringUtils.hasText(chat.getContent())) {
                continue;
            }
            messages.add(messageOf(normalizeRole(chat.getRole()), chat.getContent()));
        }

        // 若历史中尚未包含本轮消息（例如落库时机差异），则补上，避免丢失当前提问
        if (messages.isEmpty() || !isSameUserMessage(messages.get(messages.size() - 1), message)) {
            messages.add(messageOf(ROLE_USER, message));
        }
        return messages;
    }

    /**
     * 判断给定消息是否为「本轮用户消息」
     */
    private boolean isSameUserMessage(Map<String, Object> last, String message) {
        return ROLE_USER.equals(last.get("role")) && String.valueOf(message).equals(last.get("content"));
    }

    /**
     * 角色归一化：仅保留 user / assistant 两种，其余一律归为用户
     */
    private String normalizeRole(String role) {
        return ROLE_ASSISTANT.equals(role) ? ROLE_ASSISTANT : ROLE_USER;
    }

    /**
     * 构造单条消息
     */
    private Map<String, Object> messageOf(String role, String content) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content == null ? "" : content);
        return msg;
    }

    /**
     * 构建工具调用后的「随访」请求体
     *
     * <p>在基础请求体后追加：assistant 的 tool_calls 消息 + 各工具的返回结果（role=tool），
     * 供模型基于工具结果生成最终回答。</p>
     *
     * @param message          本轮用户消息
     * @param sessionId        会话ID
     * @param userId           用户ID
     * @param assistantToolCalls 归并后的工具调用（含 id / name / arguments）
     * @param toolResults      工具执行结果（含 id / content）
     * @return 第二轮请求体
     */
    public Map<String, Object> buildToolFollowUpBody(String message, String sessionId, Long userId,
                                                     List<Map<String, Object>> assistantToolCalls,
                                                     List<Map<String, Object>> toolResults) {
        Map<String, Object> body = buildRequestBody(message, sessionId, userId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");

        // assistant：声明其发起的工具调用
        List<Map<String, Object>> calls = new ArrayList<>();
        for (Map<String, Object> call : assistantToolCalls) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", call.get("name"));
            function.put("arguments", call.get("arguments"));
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", call.get("id"));
            node.put("type", "function");
            node.put("function", function);
            calls.add(node);
        }
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", ROLE_ASSISTANT);
        assistant.put("content", null);
        assistant.put("tool_calls", calls);
        messages.add(assistant);

        // tool：逐个返回执行结果
        for (Map<String, Object> result : toolResults) {
            Map<String, Object> toolMsg = new LinkedHashMap<>();
            toolMsg.put("role", "tool");
            toolMsg.put("tool_call_id", result.get("id"));
            toolMsg.put("content", result.get("content"));
            messages.add(toolMsg);
        }
        return body;
    }
}
