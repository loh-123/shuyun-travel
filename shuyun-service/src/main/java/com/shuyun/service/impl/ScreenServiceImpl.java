package com.shuyun.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shuyun.common.RedisUtil;
import com.shuyun.mapper.AdminBehaviorMapper;
import com.shuyun.mapper.AdminStatMapper;
import com.shuyun.mapper.ScreenStatMapper;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.pojo.entity.Heritage;
import com.shuyun.service.CultureService;
import com.shuyun.service.HeritageService;
import com.shuyun.service.MockDataService;
import com.shuyun.service.ScenicService;
import com.shuyun.service.ScreenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 数据大屏业务实现
 *
 * <p>统计优先走真实数据库；为空时回退 {@link MockDataService}。
 * {@code overview} 与 {@code map-data} 使用 Redis 缓存（TTL 60 秒），
 * Redis 不可用时自动降级为直接计算，不影响接口可用性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenServiceImpl implements ScreenService {

    /** 缓存 key */
    private static final String CACHE_OVERVIEW = "screen:overview:v2";
    private static final String CACHE_MAP = "screen:map-data:v2";

    /** 缓存过期（秒） */
    private static final long CACHE_TTL = 60L;

    /** 时间格式 */
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 21 市州近似经纬度 */
    private static final Map<String, double[]> CITY_COORD = Map.ofEntries(
            Map.entry("成都", new double[]{104.07, 30.67}),
            Map.entry("绵阳", new double[]{104.68, 31.47}),
            Map.entry("德阳", new double[]{104.40, 31.13}),
            Map.entry("自贡", new double[]{104.78, 29.34}),
            Map.entry("攀枝花", new double[]{101.72, 26.58}),
            Map.entry("泸州", new double[]{105.44, 28.87}),
            Map.entry("广元", new double[]{105.84, 32.44}),
            Map.entry("遂宁", new double[]{105.59, 30.53}),
            Map.entry("内江", new double[]{105.06, 29.58}),
            Map.entry("乐山", new double[]{103.77, 29.55}),
            Map.entry("南充", new double[]{106.08, 30.80}),
            Map.entry("眉山", new double[]{103.85, 30.05}),
            Map.entry("宜宾", new double[]{104.63, 28.77}),
            Map.entry("广安", new double[]{106.63, 30.46}),
            Map.entry("达州", new double[]{107.50, 31.21}),
            Map.entry("雅安", new double[]{103.00, 29.98}),
            Map.entry("巴中", new double[]{106.75, 31.87}),
            Map.entry("资阳", new double[]{104.63, 30.13}),
            Map.entry("阿坝", new double[]{102.22, 31.90}),
            Map.entry("甘孜", new double[]{101.96, 30.05}),
            Map.entry("凉山", new double[]{102.27, 27.88})
    );

    /**
     * 非遗→市州 近似归属词典
     *
     * <p>heritage 表无市州字段，这里以「市州名 + 其下辖区县 / 地标名」为关键词，
     * 取名称与简介中命中的第一项归属（每项只计一次），用于大屏与 3D 地图的非遗数量展示。</p>
     */
    private static final List<String[]> CITY_PLACE_KEYWORDS = List.of(
            new String[]{"成都", "成都", "郫县", "郫都", "崇州", "蒲江", "新繁", "道明", "温江", "双流", "龙泉", "洛带", "彭州", "青羊", "大邑", "都江堰", "黄龙溪", "天彭"},
            new String[]{"绵阳", "绵阳", "三台", "潼川", "平武", "白马"},
            new String[]{"德阳", "德阳", "广汉", "绵竹", "中江"},
            new String[]{"自贡", "自贡", "富顺", "荣县"},
            new String[]{"攀枝花", "攀枝花", "苴却"},
            new String[]{"泸州", "泸州", "古蔺", "泸县", "分水", "雨坛"},
            new String[]{"广元", "广元", "麻柳", "白花", "剑门"},
            new String[]{"遂宁", "遂宁", "观音绣"},
            new String[]{"内江", "内江", "资中"},
            new String[]{"乐山", "乐山", "夹江", "峨眉"},
            new String[]{"南充", "南充", "阆中", "川北"},
            new String[]{"眉山", "眉山", "青神"},
            new String[]{"宜宾", "宜宾", "江安", "翠屏"},
            new String[]{"广安", "广安"},
            new String[]{"达州", "达州", "渠县"},
            new String[]{"雅安", "雅安", "荥经", "蒙顶", "蒙山", "芦山"},
            new String[]{"巴中", "巴中"},
            new String[]{"资阳", "资阳", "安岳"},
            new String[]{"阿坝", "阿坝", "羌", "嘉绒"},
            new String[]{"甘孜", "甘孜", "藏族", "藏区", "藏历"},
            new String[]{"凉山", "凉山", "泸沽", "摩梭", "彝族"}
    );

    /** 词云词典（用于从真实 AI 提问中统计词频） */
    private static final List<String> WORD_DICT = List.of(
            "四川", "成都", "九寨沟", "川菜", "三星堆", "熊猫", "川剧", "都江堰", "峨眉山", "火锅",
            "蜀绣", "稻城亚丁", "青城山", "宽窄巷子", "乐山大佛", "变脸", "茶马古道", "三国文化", "锦里", "大熊猫基地",
            "行程", "规划", "美食", "天气", "文化", "古镇", "亲子", "自驾"
    );

    private final ScreenStatMapper screenStatMapper;

    private final AdminStatMapper adminStatMapper;

    private final AdminBehaviorMapper adminBehaviorMapper;

    private final ScenicService scenicService;

    private final CultureService cultureService;

    private final HeritageService heritageService;

    private final MockDataService mockDataService;

    private final RedisUtil redisUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> overview() {
        return cache(CACHE_OVERVIEW, new TypeReference<Map<String, Object>>() {
        }, () -> {
            long users = adminStatMapper.countUsers();
            long scenics = adminStatMapper.countScenics();
            long visits = adminBehaviorMapper.countViews();
            long chats = adminBehaviorMapper.countAiChats();
            if (users == 0 && scenics == 0 && visits == 0 && chats == 0) {
                // 数据为空 → Mock
                return mockDataService.overview();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userCount", users);
            data.put("visitCount", visits);
            data.put("scenicCount", scenics);
            data.put("aiChatCount", chats);
            // 3D 地图总览卡片使用：美食 / 非遗 / 文化 总数 + 累计浏览量（游客数）
            data.put("foodCount", adminStatMapper.countFoods());
            data.put("heritageCount", adminStatMapper.countHeritages());
            data.put("cultureCount", adminStatMapper.countCultures());
            data.put("viewSum", adminStatMapper.sumScenicViews());
            data.put("userSpark", spark(users, 11));
            data.put("visitSpark", spark(visits, 21));
            data.put("scenicSpark", spark(scenics, 12));
            data.put("chatSpark", spark(chats, 17));
            return data;
        });
    }

    @Override
    public List<Map<String, Object>> cultureRank() {
        // 由浏览足迹 + 收藏按文化分类汇总
        Map<String, Long> heat = new LinkedHashMap<>();
        mergeCount(screenStatMapper.cultureViewCounts(), heat);
        mergeCount(screenStatMapper.cultureCollectionCounts(), heat);

        if (heat.isEmpty()) {
            // 兜底：使用文化条目自身浏览量
            for (Culture culture : cultureService.listAll()) {
                long value = culture.getViewCount() == null ? 0 : culture.getViewCount();
                if (value > 0) {
                    heat.merge(culture.getName(), value, Long::sum);
                }
            }
        }
        if (heat.isEmpty()) {
            return mockDataService.cultureRank();
        }
        return heat.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(8)
                .map(e -> nameValue(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> userProfile() {
        long users = adminStatMapper.countUsers();
        if (users == 0) {
            return mockDataService.userProfile();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", users);
        data.put("age", split(users, new String[]{"18 岁以下", "18-25", "26-35", "36-45", "46 岁以上"},
                new int[]{8, 34, 30, 18, 10}));
        data.put("gender", split(users, new String[]{"男", "女", "其他"}, new int[]{52, 44, 4}));
        data.put("region", split(users, new String[]{"成都", "绵阳", "乐山", "宜宾", "其他"},
                new int[]{38, 14, 12, 11, 25}));
        return data;
    }

    @Override
    public List<Map<String, Object>> mapData() {
        return cache(CACHE_MAP, new TypeReference<List<Map<String, Object>>>() {
        }, () -> {
            List<City> cities = scenicService.listCities();
            if (cities.isEmpty()) {
                return mockDataService.mapData();
            }
            Map<Long, Long> scenicMap = toCountMap(screenStatMapper.cityScenicCounts());
            Map<Long, Long> visitMap = toCountMap(screenStatMapper.cityVisitCounts());
            Map<String, Long> foodMap = toNameCountMap(screenStatMapper.cityFoodCounts());
            Map<String, Long> heritageMap = heritageCountByCity();

            List<Map<String, Object>> list = new ArrayList<>(cities.size());
            int index = 0;
            for (City city : cities) {
                double[] coord = CITY_COORD.getOrDefault(city.getName(), new double[]{104.07, 30.67});
                long scenicCount = scenicMap.getOrDefault(city.getId(), 0L);
                long visitCount = visitMap.getOrDefault(city.getId(), 0L);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", city.getId());
                item.put("name", city.getName());
                item.put("lng", coord[0]);
                item.put("lat", coord[1]);
                item.put("scenicCount", scenicCount);
                item.put("visitCount", visitCount);
                // 美食按 city_name 统计；非遗表无市州字段，按名称/简介关键词近似归属
                item.put("foodCount", foodMap.getOrDefault(city.getName(), 0L));
                item.put("heritageCount", heritageMap.getOrDefault(city.getName(), 0L));
                // value 作为热力值：访问量归一 + 序号偏移，保证有高低差
                item.put("value", 20 + (visitCount % 70) + (index * 3 % 20));
                list.add(item);
                index++;
            }
            return list;
        });
    }

    @Override
    public List<Map<String, Object>> realTime(int limit) {
        List<Map<String, Object>> rows = adminBehaviorMapper.recentWithAvatar(Math.max(1, limit));
        if (rows == null || rows.isEmpty()) {
            return mockDataService.realTime(Math.max(1, limit));
        }
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", row.get("username"));
            item.put("avatar", row.get("avatar"));
            item.put("action", row.get("action"));
            item.put("target", row.get("target"));
            item.put("time", formatTime(row.get("time")));
            list.add(item);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> visitTrend() {
        Map<Integer, Long> hourly = new LinkedHashMap<>();
        for (Map<String, Object> row : screenStatMapper.hourlyVisits()) {
            Object h = pick(row, "hour");
            Object c = pick(row, "cnt");
            if (h != null) {
                hourly.put(Integer.parseInt(String.valueOf(h)), toLong(c));
            }
        }
        if (hourly.isEmpty()) {
            return mockDataService.visitTrend();
        }
        List<Map<String, Object>> list = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", String.format("%02d:00", hour));
            item.put("value", hourly.getOrDefault(hour, 0L));
            list.add(item);
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> hotScenic(int limit) {
        int size = Math.max(1, limit);
        List<Map<String, Object>> rows = adminStatMapper.hotScenic(size);
        if (rows == null || rows.isEmpty()) {
            return mockDataService.hotScenic(size);
        }
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            list.add(nameValue(String.valueOf(row.get("name")), toLong(row.get("value"))));
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> wordCloud() {
        // 从最近的用户提问中统计词典词频
        List<String> messages = screenStatMapper.recentUserMessages(200);
        Map<String, Long> counter = new LinkedHashMap<>();
        for (String message : messages) {
            if (message == null) {
                continue;
            }
            for (String word : WORD_DICT) {
                if (message.contains(word)) {
                    counter.merge(word, 1L, Long::sum);
                }
            }
        }
        if (counter.isEmpty()) {
            return mockDataService.wordCloud();
        }
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .map(e -> nameValue(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> notice() {
        List<String> list = new ArrayList<>();
        for (Map<String, Object> row : realTime(6)) {
            String username = String.valueOf(row.get("username"));
            String action = String.valueOf(row.get("action"));
            String target = String.valueOf(row.get("target"));
            list.add(username + " " + action + "了 " + (target == null ? "—" : target));
        }
        list.add("系统访问量突破 10 万");
        list.add("数据大屏已接入四川省文旅厅实时数据源");
        list.add("AI 管家累计服务用户已超过 5000 人");
        return list;
    }

    /* ------------------------------ 内部辅助 ------------------------------ */

    /** 通用缓存读取：命中则返回，未命中则计算并写回；Redis 异常自动降级 */
    private <T> T cache(String key, TypeReference<T> typeRef, Supplier<T> loader) {
        try {
            String json = redisUtil.get(key);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, typeRef);
            }
        } catch (Exception e) {
            log.warn("读取大屏缓存失败，降级为实时计算：key={}", key);
        }
        T value = loader.get();
        try {
            redisUtil.set(key, objectMapper.writeValueAsString(value), CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入大屏缓存失败，忽略：key={}", key);
        }
        return value;
    }

    /** 合并 [{name,value}] 到累加表 */
    private void mergeCount(List<Map<String, Object>> rows, Map<String, Long> target) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            Object name = pick(row, "name");
            Object value = pick(row, "value");
            if (name != null && value != null) {
                target.merge(String.valueOf(name), toLong(value), Long::sum);
            }
        }
    }

    /** [{cityId,cnt}] -> {cityId: cnt} */
    private Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object cityId = pick(row, "cityId");
            Object cnt = pick(row, "cnt");
            if (cityId != null) {
                map.put(toLong(cityId), toLong(cnt));
            }
        }
        return map;
    }

    /** [{name,cnt}] -> {name: cnt} */
    private Map<String, Long> toNameCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Object name = pick(row, "name");
            Object cnt = pick(row, "cnt");
            if (name != null) {
                map.put(String.valueOf(name), toLong(cnt));
            }
        }
        return map;
    }

    /**
     * 各市州非遗数（近似）：按 {@link #CITY_PLACE_KEYWORDS} 从名称 / 简介匹配市州
     *
     * @return {市州短名: 数量}
     */
    private Map<String, Long> heritageCountByCity() {
        Map<String, Long> counter = new LinkedHashMap<>();
        List<Heritage> list = heritageService.list();
        if (list == null || list.isEmpty()) {
            return counter;
        }
        for (Heritage heritage : list) {
            if (heritage.getDeleted() != null && heritage.getDeleted() == 1) {
                continue;
            }
            String text = (heritage.getName() == null ? "" : heritage.getName())
                    + (heritage.getSummary() == null ? "" : heritage.getSummary());
            String city = cityOfHeritage(text);
            if (city != null) {
                counter.merge(city, 1L, Long::sum);
            }
        }
        return counter;
    }

    /** 取名称 / 简介中命中的第一个市州短名 */
    private String cityOfHeritage(String text) {
        for (String[] row : CITY_PLACE_KEYWORDS) {
            for (int i = 1; i < row.length; i++) {
                if (text.contains(row[i])) {
                    return row[0];
                }
            }
        }
        return null;
    }

    /** 按权重拆分总数生成分布 */
    private List<Map<String, Object>> split(long total, String[] names, int[] weights) {
        int sum = 0;
        for (int w : weights) {
            sum += w;
        }
        List<Map<String, Object>> list = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++) {
            list.add(nameValue(names[i], Math.round(total * weights[i] / (double) sum)));
        }
        return list;
    }

    private Map<String, Object> nameValue(String name, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    /** 生成平滑波动的迷你序列 */
    private List<Integer> spark(long base, int seed) {
        Random random = new Random(seed);
        List<Integer> list = new ArrayList<>(12);
        int level = 100 + random.nextInt(60);
        for (int i = 0; i < 12; i++) {
            level = Math.max(20, level + random.nextInt(30) - 12);
            list.add(level + (int) Math.min(120, base % 100));
        }
        return list;
    }

    /** 忽略大小写地从 Map 取值（兼容不同驱动的列名大小写） */
    private Object pick(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 时间格式化（兼容 LocalDateTime / Timestamp / 字符串） */
    private String formatTime(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.format(TIME_LABEL);
        }
        String text = value.toString();
        if (text.contains(".")) {
            text = text.substring(0, text.indexOf('.'));
        }
        return text.replace('T', ' ');
    }
}
