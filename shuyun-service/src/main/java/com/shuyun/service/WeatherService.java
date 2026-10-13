package com.shuyun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Locale;
import java.util.Map;

/**
 * 天气服务
 *
 * <p>调用 Open-Meteo 免费天气接口（https://open-meteo.com，<b>无需 API Key</b>），
 * 把返回整理成 {@code {city, temp, text, tip, icon, wind, uv, daily}} 结构。</p>
 *
 * <p>网络不可用 / 接口异常 / 解析失败时返回一份合理的兜底数据，保证页面不会空窗。</p>
 *
 * <p>该类位于 shuyun-service，便于 Web 接口与 AI 工具（Function Calling）共同复用。</p>
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
                    + "&current=temperature_2m,weather_code,wind_speed_10m,uv_index,relative_humidity_2m"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=3"
                    + "&timezone=Asia%%2FShanghai";

    /** 请求超时（秒） */
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 和风天气 API Key（配置后才启用；默认空，回退 Open-Meteo） */
    @Value("${shuyun.qweather.key:}")
    private String qweatherKey;

    /** 和风天气 API 主机（各账号可能不同，按控制台分配填写） */
    @Value("${shuyun.qweather.host:devapi.qweather.com}")
    private String qweatherHost;

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
     * @return {city, temp, text, tip, icon, wind, uv, daily}
     */
    public Map<String, Object> current(String city) {
        String name = StringUtils.hasText(city) ? city.trim() : "成都";
        double[] coord = CITY_COORDS.getOrDefault(name, DEFAULT_COORDS);

        // 1) 配置了和风天气 Key 时，优先使用和风天气
        if (StringUtils.hasText(qweatherKey)) {
            try {
                Map<String, Object> qweather = qweather(name, coord);
                if (qweather != null) {
                    return qweather;
                }
            } catch (Exception e) {
                log.warn("[weather] 和风天气调用失败，回退 Open-Meteo：{}", e.getMessage());
            }
        }

        // 2) Open-Meteo（免费、无需 Key）
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
     * 调用和风天气实时天气接口
     *
     * @param city  城市名
     * @param coord 坐标（[纬度, 经度]）
     * @return 天气数据；不可用时返回 null 以回退 Open-Meteo
     */
    private Map<String, Object> qweather(String city, double[] coord) throws Exception {
        // 和风要求 location 为「经度,纬度」（最多两位小数）
        String location = String.format(Locale.ROOT, "%.2f,%.2f", coord[1], coord[0]);
        String url = "https://" + qweatherHost + "/v7/weather/now?location=" + location + "&key=" + qweatherKey;
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.body());
        if (!"200".equals(root.path("code").asText())) {
            return null;
        }
        JsonNode now = root.path("now");
        double temp = now.path("temp").asDouble();
        String text = now.path("text").asText("多云");
        String humidity = now.path("humidity").asText("—") + "%";
        String wind = now.path("windScale").asText("—") + " 级 · " + now.path("windDir").asText("");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", city);
        data.put("temp", String.valueOf(Math.round(temp)));
        data.put("text", text);
        data.put("tip", tipOf(temp, 0));
        data.put("icon", text.contains("雨") ? "rain" : (text.contains("晴") ? "sun" : "cloud"));
        data.put("wind", wind);
        data.put("uv", qweatherUv(location));
        data.put("humidity", humidity);
        data.put("daily", new ArrayList<>());
        return data;
    }

    /**
     * 和风天气紫外线指数（1 天预报，type=2）
     */
    private String qweatherUv(String location) {
        try {
            String url = "https://" + qweatherHost + "/v7/indices/1d?type=2&location=" + location + "&key=" + qweatherKey;
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "—";
            }
            JsonNode daily = objectMapper.readTree(response.body()).path("daily");
            if (daily.isArray() && daily.size() > 0) {
                return daily.get(0).path("category").asText("—");
            }
        } catch (Exception e) {
            log.debug("[weather] 和风紫外线查询失败：{}", e.getMessage());
        }
        return "—";
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
        double humidity = current.path("relative_humidity_2m").asDouble(-1);
        return build(city, temp, code, wind, uv, humidity, parseDaily(root.path("daily")));
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
        return build(city, 22, 2, 2.6, 4, 60, daily);
    }

    /**
     * 组装返回结构
     */
    private Map<String, Object> build(String city, double temp, int code, double wind, double uv,
                                     double humidity, List<Map<String, Object>> daily) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", city);
        data.put("temp", String.valueOf(Math.round(temp)));
        data.put("text", textOf(code));
        data.put("tip", tipOf(temp, code));
        data.put("icon", iconOf(code));
        data.put("wind", windText(wind));
        data.put("uv", uvText(uv));
        data.put("humidity", humidity < 0 ? "—" : Math.round(humidity) + "%");
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
