package com.shuyun.ai.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuyun.mapper.FoodShopMapper;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.FoodShop;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.ScenicService;
import com.shuyun.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 女娲 AI 工具服务（Function Calling）
 *
 * <p>向女娲平台声明 OpenAI tools 格式的函数清单，并在模型发起 tool_calls 时执行对应工具：</p>
 * <ul>
 *     <li>{@code getWeather}  查询城市天气（复用 {@link WeatherService}，Open-Meteo，无需 Key）</li>
 *     <li>{@code searchScenic} 按关键词 / 城市 / 类型查询景点</li>
 *     <li>{@code planRoute}   按起止地 + 天数生成逐日行程（基于景点库的轻量规划）</li>
 *     <li>{@code recommendFood} 按城市 / 口味查询美食店铺（{@code food_shop} 表）</li>
 *     <li>{@code getEmergency} 返回应急电话（报警/急救/火警/文旅热线）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NuwaToolService {

    private final WeatherService weatherService;

    private final ScenicService scenicService;

    private final FoodShopMapper foodShopMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 单次工具返回的最大条数 */
    private static final int LIMIT = 5;

    /**
     * 声明工具清单（OpenAI tools 格式）
     *
     * @return tools 数组，供请求体 {@code tools} 字段使用
     */
    public List<Map<String, Object>> schemas() {
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(fn("getWeather", "查询指定城市的实时天气（温度、天气、湿度、风力、紫外线）",
                Map.of("city", param("string", "城市名，如 成都"),
                        "date", param("string", "日期，格式 YYYY-MM-DD（可选）")),
                List.of("city")));
        tools.add(fn("searchScenic", "按关键词 / 城市 / 类型查询四川景点",
                Map.of("keyword", param("string", "关键词，如 九寨沟（可选）"),
                        "city", param("string", "城市名，如 成都（可选）"),
                        "type", param("string", "景点类型，如 自然风光 / 人文古迹（可选）")),
                List.of()));
        tools.add(fn("planRoute", "生成逐日行程规划",
                Map.of("from", param("string", "出发地，如 成都"),
                        "to", param("string", "目的地，如 九寨沟"),
                        "days", param("integer", "行程天数"),
                        "interests", param("string", "兴趣偏好，如 自然风光,人文古迹（可选）")),
                List.of("to", "days")));
        tools.add(fn("recommendFood", "推荐当地美食店铺",
                Map.of("location", param("string", "城市名，如 成都"),
                        "taste", param("string", "口味，如 麻辣 / 火锅 / 小吃（可选）")),
                List.of("location")));
        tools.add(fn("getEmergency", "查询应急电话（医院 / 警察 / 救援）",
                Map.of("location", param("string", "所在位置或城市（可选）")),
                List.of()));
        return tools;
    }

    /**
     * 执行工具
     *
     * @param name     工具名
     * @param argsJson 参数 JSON（模型给出的 arguments 字符串）
     * @return 工具执行结果（JSON 字符串）
     */
    public String execute(String name, String argsJson) {
        JsonNode args;
        try {
            args = StringUtils.hasText(argsJson) ? objectMapper.readTree(argsJson) : objectMapper.createObjectNode();
        } catch (Exception e) {
            args = objectMapper.createObjectNode();
        }
        try {
            Object result = switch (name == null ? "" : name) {
                case "getWeather" -> getWeather(text(args, "city"));
                case "searchScenic" -> searchScenic(text(args, "keyword"), text(args, "city"), text(args, "type"));
                case "planRoute" -> planRoute(text(args, "from"), text(args, "to"),
                        args.path("days").asInt(3), text(args, "interests"));
                case "recommendFood" -> recommendFood(text(args, "location"), text(args, "taste"));
                case "getEmergency" -> getEmergency(text(args, "location"));
                default -> Map.of("error", "未知工具：" + name);
            };
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("执行工具失败：name={}, err={}", name, e.getMessage());
            return "{\"error\":\"工具执行失败\"}";
        }
    }

    /* ------------------------------ 具体工具 ------------------------------ */

    /** 天气 */
    private Map<String, Object> getWeather(String city) {
        Map<String, Object> weather = weatherService.current(StringUtils.hasText(city) ? city : "成都");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", weather.get("city"));
        data.put("temp", weather.get("temp"));
        data.put("text", weather.get("text"));
        data.put("humidity", weather.get("humidity"));
        data.put("wind", weather.get("wind"));
        data.put("uv", weather.get("uv"));
        data.put("tip", weather.get("tip"));
        return data;
    }

    /** 景点检索 */
    private List<Map<String, Object>> searchScenic(String keyword, String city, String type) {
        Long cityId = cityIdOf(city);
        List<ScenicSpotVO> list = scenicService.pageScenic(1, LIMIT, cityId, type, keyword, "score").getRecords();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScenicSpotVO spot : list) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", spot.getId());
            item.put("name", spot.getName());
            item.put("cityName", spot.getCityName());
            item.put("type", spot.getType());
            item.put("level", spot.getLevel());
            item.put("score", spot.getScore());
            item.put("tags", spot.getTags());
            result.add(item);
        }
        return result;
    }

    /** 轻量行程规划：按目的地高分景点铺排到每一天 */
    private Map<String, Object> planRoute(String from, String to, int days, String interests) {
        int dayCount = Math.max(1, Math.min(days, 7));
        Long cityId = cityIdOf(to);
        List<ScenicSpotVO> spots = scenicService.pageScenic(1, dayCount * 2, cityId, null, to, "score").getRecords();

        List<Map<String, Object>> plan = new ArrayList<>();
        int cursor = 0;
        for (int day = 1; day <= dayCount; day++) {
            Map<String, Object> dayItem = new LinkedHashMap<>();
            dayItem.put("day", day);
            List<Map<String, Object>> items = new ArrayList<>();
            for (int i = 0; i < 2 && cursor < spots.size(); i++, cursor++) {
                ScenicSpotVO spot = spots.get(cursor);
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("scenic", spot.getName());
                node.put("stay", "约 3 小时");
                node.put("tip", spot.getType());
                items.add(node);
            }
            if (items.isEmpty()) {
                Map<String, Object> free = new LinkedHashMap<>();
                free.put("scenic", to + " 自由活动 / 周边古镇");
                free.put("stay", "半天");
                free.put("tip", "可按体力灵活调整");
                items.add(free);
            }
            dayItem.put("items", items);
            plan.add(dayItem);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", StringUtils.hasText(from) ? from : "成都");
        data.put("to", to);
        data.put("days", dayCount);
        data.put("interests", interests);
        data.put("plan", plan);
        return data;
    }

    /** 美食推荐 */
    private List<Map<String, Object>> recommendFood(String location, String taste) {
        List<FoodShop> shops = foodShopMapper.selectList(Wrappers.<FoodShop>lambdaQuery()
                .eq(FoodShop::getDeleted, 0)
                .like(StringUtils.hasText(location), FoodShop::getCity, location)
                .like(StringUtils.hasText(taste), FoodShop::getTaste, taste)
                .orderByDesc(FoodShop::getRating)
                .last("limit " + LIMIT));
        List<Map<String, Object>> result = new ArrayList<>();
        for (FoodShop shop : shops) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", shop.getName());
            item.put("city", shop.getCity());
            item.put("taste", shop.getTaste());
            item.put("address", shop.getAddress());
            item.put("price", shop.getPrice());
            item.put("rating", shop.getRating());
            item.put("description", shop.getDescription());
            result.add(item);
        }
        return result;
    }

    /** 应急信息 */
    private Map<String, Object> getEmergency(String location) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("location", StringUtils.hasText(location) ? location : "当前位置");
        data.put("police", "110");
        data.put("ambulance", "120");
        data.put("fire", "119");
        data.put("traffic", "122");
        data.put("tourismHotline", "12301（全国旅游服务热线）");
        data.put("localHotline", "12345（当地市民服务热线）");
        data.put("tips", "山区信号可能较弱，建议提前下载离线地图；高原地区注意防高反。");
        return data;
    }

    /* ------------------------------ 内部辅助 ------------------------------ */

    /** 城市名 → 城市ID */
    private Long cityIdOf(String city) {
        if (!StringUtils.hasText(city)) {
            return null;
        }
        return scenicService.listCities().stream()
                .filter(c -> city.equals(c.getName()))
                .map(City::getId)
                .findFirst()
                .orElse(null);
    }

    /** 取字符串参数 */
    private String text(JsonNode args, String field) {
        JsonNode node = args.path(field);
        return (node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    /** 构造一个 function 工具声明 */
    private Map<String, Object> fn(String name, String description, Map<String, Object> properties,
                                   List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    /** 构造一个参数描述 */
    private Map<String, Object> param(String type, String description) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", type);
        node.put("description", description);
        return node;
    }
}
