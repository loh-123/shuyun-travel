package com.shuyun.ai.nuwa;

import java.util.List;
import java.util.Map;

/**
 * 女娲流式分片
 *
 * <p>一次流式响应可能包含多类信息：正文增量（type=CHAT）、思考过程增量（type=THINK）、
 * 工具调用（Function Calling）、token 用量。用本记录统一承载，便于上层分别转发。</p>
 *
 * @param content   正文增量（可能为空）
 * @param reasoning 思考过程增量（可能为空）
 * @param toolCalls 工具调用增量（可能为空）
 * @param tokens    token 用量（通常只在最后一片出现）
 */
public record NuwaChunk(String content, String reasoning, List<Map<String, Object>> toolCalls, Integer tokens) {

    /** 构造一个「仅正文」分片 */
    public static NuwaChunk text(String content) {
        return new NuwaChunk(content, null, null, null);
    }

    /** 构造一个「仅思考过程」分片 */
    public static NuwaChunk reasoning(String reasoning) {
        return new NuwaChunk(null, reasoning, null, null);
    }

    /** 是否含有正文 */
    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    /** 是否含有思考过程 */
    public boolean hasReasoning() {
        return reasoning != null && !reasoning.isEmpty();
    }

    /** 是否含有工具调用 */
    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    /** 是否含有 token 用量 */
    public boolean hasTokens() {
        return tokens != null;
    }
}
