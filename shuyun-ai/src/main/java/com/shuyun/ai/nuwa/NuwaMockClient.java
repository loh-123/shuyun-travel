package com.shuyun.ai.nuwa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 女娲智能体模拟客户端
 *
 * <p>在 {@code nuwa.mode=mock}（默认，未配置 mode 时同样生效）下启用。
 * 按关键词返回一段四川旅游相关的模拟回复，并以逐字方式每 50ms 推送一个字，
 * 用于在前端验证 SSE 流式接收与 Markdown 渲染效果。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "nuwa.mode", havingValue = "mock", matchIfMissing = true)
public class NuwaMockClient implements NuwaClient {

    /** 逐字推送的间隔（毫秒） */
    private static final long CHAR_INTERVAL_MS = 50L;

    /** 关键词：成都 */
    private static final String KEY_CHENGDU = "成都";

    /** 关键词：川菜 */
    private static final String KEY_SICHUAN_CUISINE = "川菜";

    /** 关键词：九寨沟 */
    private static final String KEY_JIUZHAIGOU = "九寨沟";

    /** 关键词：三星堆 */
    private static final String KEY_SANXINGDUI = "三星堆";

    /** 成都 · 模拟回复 */
    private static final String REPLY_CHENGDU = "成都是四川的省会，有 2300 多年建城史。"
            + "推荐你去宽窄巷子、锦里、武侯祠、杜甫草堂，还可以去大熊猫繁育研究基地看滚滚。"
            + "美食一定要尝龙抄手、钟水饺、担担面、麻婆豆腐。"
            + "三天行程建议：第一天市区文化，第二天都江堰青城山，第三天熊猫基地+川菜体验。";

    /** 川菜 · 模拟回复 */
    private static final String REPLY_SICHUAN_CUISINE = "川菜是中国八大菜系之一，讲究一菜一格、百菜百味。"
            + "经典菜品有麻婆豆腐、回锅肉、宫保鸡丁、水煮鱼、夫妻肺片、鱼香肉丝。"
            + "推荐你去成都的陈麻婆豆腐、银杏川菜馆、大蓉和。";

    /** 九寨沟 · 模拟回复 */
    private static final String REPLY_JIUZHAIGOU = "九寨沟是世界自然遗产，以翠海、叠瀑、彩林、雪峰、藏情五绝闻名。"
            + "最佳季节是 9-10 月秋季，层林尽染。建议游玩 2-3 天，从成都出发可以自驾或坐飞机。"
            + "注意高原反应，带好保暖衣物。";

    /** 三星堆 · 模拟回复 */
    private static final String REPLY_SANXINGDUI = "三星堆遗址位于广汉，距今 3000-5000 年，是古蜀文明的璀璨明珠。"
            + "青铜神树、青铜面具、黄金权杖是其代表文物。建议去三星堆博物馆新馆，需要提前预约。";

    /** 默认 · 模拟回复 */
    private static final String REPLY_DEFAULT = "我是蜀韵·智游管家，很乐意为你服务。"
            + "你可以问我：四川有什么好玩的？怎么规划成都三日游？川菜有哪些代表菜？三星堆有什么神秘之处？";

    @Override
    public Flux<NuwaChunk> chatStream(String message, String sessionId, Long userId) {
        String reply = mockReply(message);
        log.info("女娲模拟流式对话：userId={}, sessionId={}, message={}, replyLen={}",
                userId, sessionId, message, reply.length());

        // 按码点切分为「字」，兼容中文与 emoji
        List<String> units = reply.codePoints()
                .mapToObj(cp -> new String(Character.toChars(cp)))
                .collect(Collectors.toList());

        // 每 50ms 推送一个字，模拟流式输出
        return Flux.interval(Duration.ofMillis(CHAR_INTERVAL_MS))
                .take(units.size())
                .map(index -> NuwaChunk.text(units.get(index.intValue())));
    }

    /**
     * 按关键词匹配模拟回复
     *
     * @param message 用户消息
     * @return 模拟回复文本
     */
    private String mockReply(String message) {
        String msg = message == null ? "" : message;
        if (msg.contains(KEY_CHENGDU)) {
            return REPLY_CHENGDU;
        }
        if (msg.contains(KEY_SICHUAN_CUISINE)) {
            return REPLY_SICHUAN_CUISINE;
        }
        if (msg.contains(KEY_JIUZHAIGOU)) {
            return REPLY_JIUZHAIGOU;
        }
        if (msg.contains(KEY_SANXINGDUI)) {
            return REPLY_SANXINGDUI;
        }
        return REPLY_DEFAULT;
    }
}
