package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.service.ScreenService;
import com.shuyun.service.ScreenStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 数据大屏接口
 *
 * <p>面向可视化大屏只读聚合接口，已在 {@code WebMvcConfig} 中放行（无需登录）。
 * 前端可轮询或后续替换为 SSE / WebSocket 推送。</p>
 */
@RestController
@RequestMapping("/api/screen")
@RequiredArgsConstructor
public class ScreenController {

    private final ScreenService screenService;

    private final ScreenStreamService screenStreamService;

    /**
     * 实时数据推送（SSE，每 5 秒一次）
     *
     * <p>事件名 {@code realtime}，data 为最近 10 条用户行为的 JSON 数组，
     * 字段与 {@code /real-time} 完全一致。</p>
     *
     * @return SSE 连接
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return screenStreamService.subscribe();
    }

    /**
     * 关键指标
     *
     * @return 指标数据（含美食 / 非遗 / 文化总数、累计浏览量 viewSum 与迷你趋势序列）
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(screenService.overview());
    }

    /**
     * 文化热度排行
     *
     * @return [{name, value}]
     */
    @GetMapping("/culture-rank")
    public Result<List<Map<String, Object>>> cultureRank() {
        return Result.ok(screenService.cultureRank());
    }

    /**
     * 用户画像
     *
     * @return {total, items:[{name, value}]}
     */
    @GetMapping("/user-profile")
    public Result<Map<String, Object>> userProfile() {
        return Result.ok(screenService.userProfile());
    }

    /**
     * 地图数据（21 市州热度）
     *
     * @return [{id, name, lng, lat, scenicCount, visitCount, foodCount, heritageCount, value}]
     */
    @GetMapping("/map-data")
    public Result<List<Map<String, Object>>> mapData() {
        return Result.ok(screenService.mapData());
    }

    /**
     * 实时数据流
     *
     * @param limit 条数，默认 10
     * @return [{username, avatar, action, target, time}]
     */
    @GetMapping("/real-time")
    public Result<List<Map<String, Object>>> realTime(@RequestParam(defaultValue = "10") int limit) {
        return Result.ok(screenService.realTime(limit));
    }

    /**
     * 近 24 小时访问趋势
     *
     * @return [{time, value}]
     */
    @GetMapping("/visit-trend")
    public Result<List<Map<String, Object>>> visitTrend() {
        return Result.ok(screenService.visitTrend());
    }

    /**
     * 热门景点 TOP N
     *
     * @param limit 数量，默认 5
     * @return [{name, value}]
     */
    @GetMapping("/hot-scenic")
    public Result<List<Map<String, Object>>> hotScenic(@RequestParam(defaultValue = "5") int limit) {
        return Result.ok(screenService.hotScenic(limit));
    }

    /**
     * AI 对话词云
     *
     * @return [{name, value}]
     */
    @GetMapping("/word-cloud")
    public Result<List<Map<String, Object>>> wordCloud() {
        return Result.ok(screenService.wordCloud());
    }

    /**
     * 底部滚动通知
     *
     * @return 通知文案列表
     */
    @GetMapping("/notice")
    public Result<List<String>> notice() {
        return Result.ok(screenService.notice());
    }
}
