package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.mapper.UserAiChatMapper;
import com.shuyun.mapper.UserProfileMapper;
import com.shuyun.pojo.entity.UserAiChat;
import com.shuyun.pojo.entity.UserProfile;
import com.shuyun.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 对话业务实现
 *
 * <p><b>当前为模拟实现</b>：回复由 {@link #mockReply(String)} 按关键词生成（Markdown 文本）。
 * 接入女娲智能体时，只需把该方法替换为真实调用，其余（会话管理、历史记录、SSE 推送）都不用改。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl extends ServiceImpl<UserAiChatMapper, UserAiChat> implements AiChatService {

    /** 角色：用户 */
    private static final String ROLE_USER = "user";

    /** 角色：AI 助手 */
    private static final String ROLE_ASSISTANT = "assistant";

    /** 会话标题最大长度 */
    private static final int TITLE_MAX = 18;

    /** 用户画像 Mapper（读取 user_profile 表） */
    private final UserProfileMapper userProfileMapper;

    @Override
    public String chat(Long userId, String message, String sessionId) {
        saveMessage(userId, sessionId, ROLE_USER, message);
        String reply = mockReply(message);
        saveMessage(userId, sessionId, ROLE_ASSISTANT, reply);
        log.info("AI 对话：userId={}, sessionId={}, messageLen={}", userId, sessionId,
                message == null ? 0 : message.length());
        return reply;
    }

    @Override
    public List<UserAiChat> listBySession(Long userId, String sessionId) {
        return list(Wrappers.<UserAiChat>lambdaQuery()
                .eq(UserAiChat::getUserId, userId)
                .eq(UserAiChat::getSessionId, sessionId)
                .orderByAsc(UserAiChat::getId));
    }

    @Override
    public List<Map<String, Object>> listSessions(Long userId) {
        List<UserAiChat> all = list(Wrappers.<UserAiChat>lambdaQuery()
                .eq(UserAiChat::getUserId, userId)
                .orderByAsc(UserAiChat::getId));
        if (all.isEmpty()) {
            return new ArrayList<>();
        }

        // 按 sessionId 聚合：标题取该会话第一条用户消息，时间取最后一条消息
        Map<String, List<UserAiChat>> grouped = all.stream()
                .filter(c -> c.getSessionId() != null)
                .collect(Collectors.groupingBy(UserAiChat::getSessionId, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> sessions = new ArrayList<>();
        grouped.forEach((sessionId, list) -> {
            String title = list.stream()
                    .filter(c -> ROLE_USER.equals(c.getRole()) && c.getContent() != null)
                    .map(UserAiChat::getContent)
                    .findFirst()
                    .orElse("新的对话");
            LocalDateTime lastTime = list.get(list.size() - 1).getCreateTime();

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", sessionId);
            item.put("title", title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) + "…" : title);
            item.put("lastTime", lastTime);
            item.put("messageCount", list.size());
            sessions.add(item);
        });

        // 最近有消息的排前面
        sessions.sort((a, b) -> {
            LocalDateTime t1 = (LocalDateTime) a.get("lastTime");
            LocalDateTime t2 = (LocalDateTime) b.get("lastTime");
            if (t1 == null || t2 == null) {
                return 0;
            }
            return t2.compareTo(t1);
        });
        return sessions;
    }

    @Override
    public void removeSession(Long userId, String sessionId) {
        remove(Wrappers.<UserAiChat>lambdaQuery()
                .eq(UserAiChat::getUserId, userId)
                .eq(UserAiChat::getSessionId, sessionId));
    }

    /**
     * 查询当前用户的 AI 画像（供对话页「记忆体」面板展示）
     */
    @Override
    public Map<String, Object> profile(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (userId == null) {
            return data;
        }
        UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, userId)
                .eq(UserProfile::getDeleted, 0)
                .last("limit 1"));
        if (profile == null) {
            return data;
        }
        if (profile.getInterests() != null) {
            data.put("interests", profile.getInterests());
        }
        if (profile.getCompanion() != null) {
            data.put("companion", profile.getCompanion());
        }
        if (profile.getBudget() != null) {
            data.put("budget", profile.getBudget());
        }
        if (profile.getRegion() != null) {
            data.put("region", profile.getRegion());
        }
        if (profile.getAgeRange() != null) {
            data.put("ageRange", profile.getAgeRange());
        }
        if (profile.getGender() != null) {
            data.put("gender", profile.getGender());
        }
        return data;
    }

    /**
     * 保存一条对话记录
     */
    @Override
    public void saveMessage(Long userId, String sessionId, String role, String content) {
        UserAiChat chat = new UserAiChat();
        chat.setUserId(userId);
        chat.setSessionId(sessionId);
        chat.setRole(role);
        chat.setContent(content);
        chat.setTokens(0);
        save(chat);
    }

    /**
     * 保存 AI 回复并记录调用指标
     */
    @Override
    public void saveAiReply(Long userId, String sessionId, String content, Integer tokens,
                            Integer inputLen, Integer outputLen, Long durationMs, boolean success) {
        UserAiChat chat = new UserAiChat();
        chat.setUserId(userId);
        chat.setSessionId(sessionId);
        chat.setRole(ROLE_ASSISTANT);
        chat.setContent(content);
        chat.setTokens(tokens == null ? 0 : tokens);
        chat.setInputLen(inputLen);
        chat.setOutputLen(outputLen);
        chat.setDurationMs(durationMs);
        chat.setSuccess(success ? 1 : 0);
        save(chat);
    }

    /**
     * 模拟回复（Markdown）。
     *
     * <p>按关键词返回不同示例答案，便于演示 Markdown 渲染与流式输出；
     * 接入女娲智能体后整体替换本方法即可，前端无需改动。</p>
     */
    private String mockReply(String message) {
        String msg = message == null ? "" : message;

        if (hit(msg, "行程", "规划", "路线", "几日", "几天", "三日", "三日游", "安排")) {
            return """
                   好的，先按 **3 天** 给你一版成都行程，节奏偏慢，适合第一次来巴蜀。

                   ## 行程建议

                   | 天数 | 安排 | 重点 |
                   | --- | --- | --- |
                   | D1 | 宽窄巷子 → 人民公园 → 锦里 | 市区慢逛，先适应节奏 |
                   | D2 | 都江堰 → 青城山 | 水利工程 + 道教名山 |
                   | D3 | 大熊猫繁育研究基地 → 武侯祠 | 早起看熊猫最活跃 |

                   ### 几点提醒

                   - 熊猫基地 **07:30 开园**，建议开园就到，上午最活跃
                   - 都江堰与青城山在同一方向，适合排成一天
                   - 市区到都江堰可乘城际列车，约 40 分钟

                   需要我把它存成行程吗？在 **个人中心 → 我的行程** 里可以直接新建并逐天编辑。
                   """;
        }

        if (hit(msg, "川菜", "美食", "吃", "小吃", "火锅")) {
            return """
                   四川的美食远不止「麻辣」，按味型给你排一排。

                   ## 必吃清单

                   1. **麻婆豆腐** —— 麻辣鲜香烫，川菜的教科书
                   2. **回锅肉** —— 川菜之首，肥而不腻
                   3. **宫保鸡丁** —— 荔枝味型，甜酸微辣
                   4. **水煮鱼** —— 麻辣之外的一层鲜
                   5. **甜皮鸭 / 钵钵鸡** —— 乐山小吃的代表

                   > 小提示：成都讲究调和，自贡盐帮菜偏鲜辣，乐山小吃更接地气，一地一味。

                   想按「城市 + 口味」再收窄一些吗？告诉我你待几天、在哪几个市州。
                   """;
        }

        if (hit(msg, "九寨", "什么时候", "季节", "最美", "什么时候去")) {
            return """
                   九寨沟四季各有各的好，看你想看什么。

                   ## 四季对照

                   - **春（4–5 月）**：水量回升，人少，适合安静看水
                   - **夏（6–8 月）**：翠海叠瀑最饱满，但雨季路况要看预报
                   - **秋（9–10 月）**：**彩林 + 蓝海子**，公认最美，也是人最多的时候
                   - **冬（11–3 月）**：雪后的海子极静，部分栈道可能关闭

                   ### 建议

                   - 想看经典的「彩林倒影」，选 **10 月中下旬**
                   - 高原早晚温差大，**薄羽绒 + 防晒** 两手都要准备
                   - 旺季门票与观光车需提前预约

                   要不要我把它并进一份川西环线的行程里？
                   """;
        }

        if (hit(msg, "三星堆", "古蜀", "青铜", "面具")) {
            return """
                   三星堆是那种「越看越不懂，但越想看」的地方。

                   ## 三件必看

                   - **青铜大立人**：通高约 2.6 米，手势至今成谜
                   - **青铜神树**：高近 4 米，被认为是神话里的「扶桑」
                   - **纵目面具**：眼球外凸，与中原青铜文明风格迥异

                   ### 为什么特别

                   它和成都金沙遗址一脉相承，说明古蜀并不是「边缘文明」，
                   而是有自己完整信仰与工艺体系的另一条文明线索。

                   建议预留 **3 小时以上**，先看新馆再看遗址区，顺序反过来会累。
                   想听川剧变脸或蜀绣的部分，也可以直接问我。
                   """;
        }

        if (hit(msg, "天气", "下雨", "气温", "穿什么")) {
            return """
                   我可以按市州查实时天气，右侧面板也会同步显示。

                   ## 出行提醒

                   - 查看右下角 **天气卡片**，温度与天气会按你提到的城市自动切换
                   - 山区（阿坝 / 甘孜 / 凉山）昼夜温差常在 **10℃ 以上**
                   - 雨季（6–8 月）建议随身带伞，山路注意落石

                   告诉我你想去的市州，我把天气和行程一起排给你。
                   """;
        }

        return """
               你好，我是 **蜀韵·智游管家**，可以帮你规划行程、推荐景点与美食。

               ### 我比较擅长的

               - **行程规划**：按天数、同行人和偏好排一条走得通的路线
               - **文化科普**：川剧、蜀绣、三星堆、三国、茶马古道等
               - **美食与天气**：川菜味型、市州实时天气

               ### 试着这样问我

               - 「帮我规划成都三日游」
               - 「四川有哪些必吃的川菜？」
               - 「九寨沟什么时候去最美？」

               > 注：当前为示例回复，接入女娲智能体后即为真实回答。
               """;
    }

    /**
     * 关键词命中判断
     */
    private boolean hit(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
