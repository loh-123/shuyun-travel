package com.shuyun.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 大屏模拟数据服务（MockDataService）
 *
 * <p>当数据库统计结果为空时，由 {@link ScreenService} 回退调用本类，返回与真实接口
 * <b>字段格式完全一致</b>的随机模拟数据，保证大屏始终有内容可展示。</p>
 */
@Service
public class MockDataService {

    /** 时间格式 */
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 21 市州近似经纬度（与真实数据保持一致的字段结构） */
    private static final Object[][] CITIES = {
            {"成都", 104.07, 30.67}, {"绵阳", 104.68, 31.47}, {"德阳", 104.40, 31.13},
            {"自贡", 104.78, 29.34}, {"攀枝花", 101.72, 26.58}, {"泸州", 105.44, 28.87},
            {"广元", 105.84, 32.44}, {"遂宁", 105.59, 30.53}, {"内江", 105.06, 29.58},
            {"乐山", 103.77, 29.55}, {"南充", 106.08, 30.80}, {"眉山", 103.85, 30.05},
            {"宜宾", 104.63, 28.77}, {"广安", 106.63, 30.46}, {"达州", 107.50, 31.21},
            {"雅安", 103.00, 29.98}, {"巴中", 106.75, 31.87}, {"资阳", 104.63, 30.13},
            {"阿坝", 102.22, 31.90}, {"甘孜", 101.96, 30.05}, {"凉山", 102.27, 27.88}
    };

    /** 文化分类 */
    private static final String[] CATEGORIES = {"戏曲", "传统技艺", "饮食文化", "历史文化", "考古文明", "自然生态"};

    /** 词云基础词库 */
    private static final String[] WORDS = {"四川", "成都", "九寨沟", "川菜", "三星堆", "熊猫", "川剧", "都江堰",
            "峨眉山", "火锅", "蜀绣", "稻城亚丁", "青城山", "宽窄巷子", "乐山大佛", "变脸", "茶马古道", "三国文化", "锦里", "大熊猫基地"};

    /** 实时流模板 */
    private static final Object[][] STREAM = {
            {"张三", "浏览", "九寨沟"}, {"李四", "生成", "成都三日游"}, {"王五", "收藏", "稻城亚丁"},
            {"赵六", "浏览", "三星堆"}, {"钱七", "AI 咨询", "川菜推荐"}, {"孙八", "浏览", "熊猫基地"},
            {"周九", "收藏", "都江堰"}, {"吴十", "生成", "川西环线"}
    };

    private final Random random = new Random();

    /** 关键指标 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", 120 + random.nextInt(80));
        data.put("visitCount", 6000 + random.nextInt(4000));
        data.put("scenicCount", 19 + random.nextInt(10));
        data.put("aiChatCount", 300 + random.nextInt(300));
        data.put("foodCount", 100 + random.nextInt(20));
        data.put("heritageCount", 100);
        data.put("cultureCount", 50);
        data.put("viewSum", 320000 + random.nextInt(20000));
        data.put("userSpark", spark());
        data.put("visitSpark", spark());
        data.put("scenicSpark", spark());
        data.put("chatSpark", spark());
        return data;
    }

    /** 文化热度排行 */
    public List<Map<String, Object>> cultureRank() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String name : CATEGORIES) {
            list.add(nameValue(name, 800 + random.nextInt(1800)));
        }
        return list;
    }

    /** 用户画像（年龄 / 性别 / 地域） */
    public Map<String, Object> userProfile() {
        long total = 180 + random.nextInt(80);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("age", distribution(new String[]{"18 岁以下", "18-25", "26-35", "36-45", "46 岁以上"}, total));
        data.put("gender", distribution(new String[]{"男", "女", "其他"}, total));
        data.put("region", distribution(new String[]{"成都", "绵阳", "乐山", "宜宾", "其他"}, total));
        return data;
    }

    /** 地图数据 */
    public List<Map<String, Object>> mapData() {
        List<Map<String, Object>> list = new ArrayList<>();
        int index = 0;
        for (Object[] city : CITIES) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", index + 1L);
            item.put("name", city[0]);
            item.put("lng", city[1]);
            item.put("lat", city[2]);
            item.put("scenicCount", 3 + random.nextInt(26));
            item.put("visitCount", 400 + random.nextInt(5000));
            item.put("foodCount", 2 + random.nextInt(12));
            item.put("heritageCount", 1 + random.nextInt(8));
            item.put("value", 40 + (index * 7 % 60));
            list.add(item);
            index++;
        }
        return list;
    }

    /** 实时流 */
    public List<Map<String, Object>> realTime(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            Object[] row = STREAM[i % STREAM.length];
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("username", row[0]);
            item.put("avatar", null);
            item.put("action", row[1]);
            item.put("target", row[2]);
            item.put("time", TIME_LABEL.format(LocalDateTime.now().minusSeconds(i * 20L)));
            list.add(item);
        }
        return list;
    }

    /** 近 24 小时访问趋势 */
    public List<Map<String, Object>> visitTrend() {
        List<Map<String, Object>> list = new ArrayList<>(24);
        for (int hour = 0; hour < 24; hour++) {
            double factor = 0.35 + 0.65 * Math.sin(Math.PI * (hour - 5) / 16.0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("time", String.format("%02d:00", hour));
            item.put("value", (int) Math.max(8, 120 * factor + random.nextInt(40)));
            list.add(item);
        }
        return list;
    }

    /** 热门景点 TOP */
    public List<Map<String, Object>> hotScenic(int limit) {
        String[] names = {"九寨沟", "稻城亚丁", "峨眉山", "都江堰", "成都大熊猫繁育研究基地", "青城山", "乐山大佛"};
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, names.length); i++) {
            list.add(nameValue(names[i], 1800 - i * 200 + random.nextInt(120)));
        }
        return list;
    }

    /** 词云 */
    public List<Map<String, Object>> wordCloud() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < WORDS.length; i++) {
            list.add(nameValue(WORDS[i], 260 + (WORDS.length - i) * 38 + random.nextInt(40)));
        }
        return list;
    }

    /** 滚动通知 */
    public List<String> notice() {
        List<String> list = new ArrayList<>();
        list.add("系统访问量突破 10 万");
        list.add("张三 刚刚浏览了 九寨沟");
        list.add("李四 生成了成都三日游");
        list.add("数据大屏已接入四川省文旅厅实时数据源");
        list.add("AI 管家累计服务用户已超过 5000 人");
        return list;
    }

    /* ------------------------------ 内部辅助 ------------------------------ */

    private Map<String, Object> nameValue(String name, long value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    /** 平滑波动的迷你趋势序列 */
    private List<Integer> spark() {
        List<Integer> list = new ArrayList<>(12);
        int level = 100 + random.nextInt(60);
        for (int i = 0; i < 12; i++) {
            level = Math.max(20, level + random.nextInt(30) - 12);
            list.add(level);
        }
        return list;
    }

    /** 按权重拆分总数，生成分布 */
    private List<Map<String, Object>> distribution(String[] names, long total) {
        List<Map<String, Object>> list = new ArrayList<>(names.length);
        int[] weights = new int[names.length];
        int sum = 0;
        for (int i = 0; i < names.length; i++) {
            weights[i] = 10 + random.nextInt(30);
            sum += weights[i];
        }
        for (int i = 0; i < names.length; i++) {
            list.add(nameValue(names[i], Math.round(total * weights[i] / (double) sum)));
        }
        return list;
    }
}
