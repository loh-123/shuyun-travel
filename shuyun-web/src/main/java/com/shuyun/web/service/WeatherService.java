package com.shuyun.web.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气服务
 *
 * <p>调用 Open-Meteo 免费天气接口（https://open-meteo.com，<b>无需 API Key</b>），
 * 把返回整理成前端需要的 {@code {city, temp, text, tip, icon}} 结构。</p>
 *
 * <p>网络不可用 / 接口异常 / 解析失败时返回一份合理的兜底数据，保证页面不会空窗。</p>
 *
 * <p>说明：本类放在 shuyun-web 而非 shuyun-service，是因为它依赖 Jackson（Web 层已有），
 * 这样 service 模块无需额外引入 HTTP 客户端依赖。</p>
 */
@Slf4j
@Service
public class WeatherService {

    /**
     * Open-Meteo 当前天气接口（%s 依次为纬度、经度）
     *
     * <p>注意：字符串会交给 String.format，因此 URL 里表示 “/” 的 %2F 必须写成 %%2F。</p>
     */
    private static final String API =
            "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                    + "&current=temperature_2m,weather_code,wind_speed_10m,uv_index"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3"
                    + "&timezone=Asia%%2FShanghai";

    /** 请求超时（秒） */
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 21 个市州坐标（纬度, 经度）
     *
     * <p>市州表未存坐标，这里内置一份；未命中时回退成都。</p>
     */
    private static final Map<String, double[]> CITY_COORDS = new LinkedHashMap<>();

    static {
        CITY_COORDS.put("成都", new double[] {30.573, 104.067});
        CITY_COORDS.put("绵阳", new double[] {31.467, 104.680});
        CITY_COORDS.put("德阳", new double[] {31.128, 104.398});
        CITY_COORDS.put("自贡", new double[] {29.339, 104.779});
        CITY_COORDS.put("攀枝花", new double[] {26.583, 101.718});
        CITY_COORDS.put("泸州", new double[] {28.872, 105.443});
        CITY_COORDS.put("广元", new double[] {32.435, 105.844});
        CITY_COORDS.put("遂宁", new double[] {30.513, 105.593});
        CITY_COORDS.put("内江", new double[] {29.581, 105.058});
        CITY_COORDS.put("乐山", new double[] {29.552, 103.766});
        CITY_COORDS.put("南充", new double[] {30.837, 106.110});
        CITY_COORDS.put("眉山", new double[] {30.049, 103.849});
        CITY_COORDS.put("宜宾", new double[] {28.752, 104.630});
        CITY_COORDS.put("广安", new double[] {30.456, 106.633});
        CITY_COORDS.put("达州", new double[] {31.209, 107.468});
        CITY_COORDS.put("雅安", new double[] {29.980, 103.001});
        CITY_COORDS.put("巴中", new double[] {31.869, 106.748});
        CITY_COORDS.put("资阳", new double[] {30.129, 104.627});
        CITY_COORDS.put("阿坝", new double[] {31.899, 102.221});
        CITY_COORDS.put("甘孜", new double[] {30.049, 101.963});
        CITY_COORDS.put("凉山", new double[] {27.881, 102.268});
    }

    private static final double[] DEFAULT_COORDS = CITY_COORDS.get("成都");

    /**
     * 查询城市实时天气
     *
     * @param city 市州名称（可为空，默认成都）
     * @return {city, temp, text, tip, icon}
     */
    public Map<String, Object> current(String city) {
        String name = StringUtils.hasText(city) ? city.trim() : "成都";
        double[] coord = CITY_COORDS.getOrDefault(name, DEFAULT_COORDS);

        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(String.format(API, coord[0], coord[1])))
                    .timeout(TIMEOUT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parse(name, response.body());
            }
            log.warn("[weather] Open-Meteo 返回状态码 {}", response.statusCode());
        } catch (Exception e) {
            log.warn("[weather] 调用 Open-Meteo 失败，使用兜底数据：{}", e.getMessage());
        }
        return fallback(name);
    }

    /**
     * 解析 Open-Meteo 响应
     */
    private Map<String, Object> parse(String city, String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode current = root.path("current");
        JsonNode tempNode = current.path("temperature_2m");
        if (tempNode.isMissingNode() || tempNode.isNull()) {
            return fallback(city);
        }
        double temp = tempNode.asDouble();
        int code = current.path("weather_code").asInt(0);
        double wind = current.path("wind_speed_10m").asDouble(0);
        double uv = current.path("uv_index").asDouble(0);
        return build(city, temp, code, wind, uv, parseDaily(root.path("daily")));
    }

    /**
     * 解析 3 日预报
     */
    private List<Map<String, Object>> parseDaily(JsonNode daily) {
        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode dates = daily.path("time");
        JsonNode codes = daily.path("weather_code");
        JsonNode maxs = daily.path("temperature_2m_max");
        JsonNode mins = daily.path("temperature_2m_min");

        for (int i = 0; i < dates.size(); i++) {
            int code = codes.path(i).asInt(0);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", dates.path(i).asText());
            day.put("text", textOf(code));
            day.put("icon", iconOf(code));
            day.put("max", Math.round(maxs.path(i).asDouble()));
            day.put("min", Math.round(mins.path(i).asDouble()));
            list.add(day);
        }
        return list;
    }

    /**
     * 兜底数据（接口不可用时使用）
     */
    private Map<String, Object> fallback(String city) {
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", LocalDate.now().plusDays(i).toString());
            day.put("text", "多云");
            day.put("icon", "cloud");
            day.put("max", 24 + i);
            day.put("min", 16 + i);
            daily.add(day);
        }
        return build(city, 22, 2, 2.6, 4, daily);
    }

    /**
     * 组装返回结构
     */
    private Map<String, Object> build(String city, double temp, int code, double wind, double uv,
                                     List<Map<String, Object>> daily) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", city);
        data.put("temp", String.valueOf(Math.round(temp)));
        data.put("text", textOf(code));
        data.put("tip", tipOf(temp, code));
        data.put("icon", iconOf(code));
        data.put("wind", windText(wind));
        data.put("uv", uvText(uv));
        data.put("daily", daily);
        return data;
    }

    /**
     * 风速（m/s）-> 风力等级描述
     */
    private String windText(double speed) {
        if (speed < 0.3) {
            return "0 级 · 无风";
        }
        if (speed < 1.6) {
            return "1 级 · 软风";
        }
        if (speed < 3.4) {
            return "2 级 · 轻风";
        }
        if (speed < 5.5) {
            return "3 级 · 微风";
        }
        if (speed < 8.0) {
            return "4 级 · 和风";
        }
        if (speed < 10.8) {
            return "5 级 · 清风";
        }
        if (speed < 13.9) {
            return "6 级 · 强风";
        }
        return "7 级以上 · 需注意";
    }

    /**
     * 紫外线指数 -> 强度描述
     */
    private String uvText(double uv) {
        if (uv < 3) {
            return "弱";
        }
        if (uv < 6) {
            return "中等（建议防晒）";
        }
        if (uv < 8) {
            return "强（注意防晒）";
        }
        if (uv < 11) {
            return "很强（务必防晒）";
        }
        return "极强";
    }

    /**
     * WMO 天气代码 -> 中文描述
     */
    private String textOf(int code) {
        if (code == 0) {
            return "晴";
        }
        if (code <= 2) {
            return "多云";
        }
        if (code == 3) {
            return "阴";
        }
        if (code == 45 || code == 48) {
            return "雾";
        }
        if (code >= 51 && code <= 57) {
            return "小雨";
        }
        if (code >= 61 && code <= 67) {
            return "中雨";
        }
        if (code >= 71 && code <= 77) {
            return "雪";
        }
        if (code >= 80 && code <= 82) {
            return "阵雨";
        }
        if (code == 85 || code == 86) {
            return "阵雪";
        }
        if (code >= 95) {
            return "雷阵雨";
        }
        return "多云";
    }

    /**
     * 天气代码 -> 前端图标类型（sun / cloud / rain）
     */
    private String iconOf(int code) {
        if (code == 0 || code == 1) {
            return "sun";
        }
        if (code >= 51) {
            return "rain";
        }
        return "cloud";
    }

    /**
     * 出行提示
     */
    private String tipOf(double temp, int code) {
        if (code >= 51) {
            return "记得带伞，路面可能湿滑";
        }
        if (temp >= 30) {
            return "天气炎热，注意防晒补水";
        }
        if (temp <= 5) {
            return "气温偏低，注意保暖";
        }
        return "天气不错，适合出行";
    }
}
