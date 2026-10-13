package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 天气接口
 *
 * <p>数据源为 Open-Meteo（免费、无需 API Key），网络异常时自动返回兜底数据。</p>
 */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    /**
     * 查询城市实时天气
     *
     * @param city 市州名称（如「成都」），为空时默认成都
     * @return 统一结果，data = {city, temp, text, tip, icon}
     */
    @GetMapping
    public Result<Map<String, Object>> current(@RequestParam(required = false) String city) {
        return Result.ok(weatherService.current(city));
    }
}
