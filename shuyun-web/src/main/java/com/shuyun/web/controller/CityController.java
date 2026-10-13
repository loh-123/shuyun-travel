package com.shuyun.web.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shuyun.common.Result;
import com.shuyun.mapper.CityMapper;
import com.shuyun.mapper.FoodMapper;
import com.shuyun.mapper.HeritageMapper;
import com.shuyun.mapper.ScenicSpotMapper;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.Food;
import com.shuyun.pojo.entity.Heritage;
import com.shuyun.pojo.entity.ScenicSpot;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 城市 3D 页面接口
 *
 * <p>按「市州 + 区县」聚合景点 / 美食 / 非遗，供城市 3D 页面的右侧面板使用。</p>
 *
 * <p>数据口径说明：</p>
 * <ul>
 *   <li>景点：scenic_spot 有 city_id 与 address，按 address 解析县级名称归集；</li>
 *   <li>美食：food 有 city_name 与 shop_address，按 shop_address 解析县级名称归集；</li>
 *   <li>非遗：heritage 表既无市州也无区县字段，此处按「项目名称 / 简介是否包含市州名」
 *       做全市归属（尽力而为），因此同一市的各区县会展示相同的非遗列表。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/city")
@RequiredArgsConstructor
public class CityController {

    private final CityMapper cityMapper;

    private final ScenicSpotMapper scenicSpotMapper;

    private final FoodMapper foodMapper;

    private final HeritageMapper heritageMapper;

    /** 县级名称：2~8 个汉字 + 区 / 县 / 市（非贪婪，如「武侯区」「都江堰市」「九寨沟县」） */
    private static final Pattern DISTRICT_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,8}?(?:区|县|市))");

    /**
     * 区县统计与明细
     *
     * @param cityName     市州名称（如「成都」）
     * @param districtName 区县名称（如「武侯区」）；为空时返回该市全部
     * @return {scenicCount, foodCount, heritageCount, scenics[], foods[], heritages[]}
     */
    @GetMapping("/district-stats")
    public Result<Map<String, Object>> districtStats(@RequestParam String cityName,
                                                     @RequestParam(required = false) String districtName) {
        City city = cityMapper.selectOne(Wrappers.<City>lambdaQuery()
                .eq(City::getName, cityName)
                .last("limit 1"));

        List<Map<String, Object>> scenics = new ArrayList<>();
        List<Map<String, Object>> foods = new ArrayList<>();
        List<Map<String, Object>> heritages = new ArrayList<>();

        if (city != null) {
            // 景点：按 city_id 取该市全部，再按地址解析的区县过滤
            List<ScenicSpot> spots = scenicSpotMapper.selectList(Wrappers.<ScenicSpot>lambdaQuery()
                    .eq(ScenicSpot::getCityId, city.getId()));
            for (ScenicSpot spot : spots) {
                if (!districtMatches(districtName, parseDistrict(spot.getAddress(), cityName))) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", spot.getId());
                item.put("name", spot.getName());
                item.put("type", spot.getType());
                item.put("coverImage", spot.getCoverImage());
                item.put("description", spot.getDescription());
                item.put("address", spot.getAddress());
                item.put("longitude", spot.getLongitude());
                item.put("latitude", spot.getLatitude());
                scenics.add(item);
            }

            // 美食：按 city_name 取该市全部，再按店铺地址解析的区县过滤
            List<Food> foodList = foodMapper.selectList(Wrappers.<Food>lambdaQuery()
                    .eq(Food::getCityName, cityName));
            for (Food food : foodList) {
                if (!districtMatches(districtName, parseDistrict(food.getShopAddress(), cityName))) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", food.getId());
                item.put("name", food.getName());
                item.put("category", food.getCategory());
                item.put("coverImage", food.getCoverImage());
                item.put("description", food.getDescription());
                item.put("shopName", food.getShopName());
                item.put("shopAddress", food.getShopAddress());
                item.put("longitude", food.getShopLongitude());
                item.put("latitude", food.getShopLatitude());
                foods.add(item);
            }
        }

        // 非遗：库中无地理字段，按项目名称 / 简介是否包含市州名做「全市」归属
        if (StringUtils.hasText(cityName)) {
            List<Heritage> allHeritage = heritageMapper.selectList(Wrappers.<Heritage>lambdaQuery());
            for (Heritage heritage : allHeritage) {
                String text = StringUtils.hasText(heritage.getName()) ? heritage.getName() : "";
                text += StringUtils.hasText(heritage.getSummary()) ? heritage.getSummary() : "";
                if (!text.contains(cityName)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", heritage.getId());
                item.put("name", heritage.getName());
                item.put("category", heritage.getCategory());
                item.put("level", heritage.getLevel());
                item.put("summary", heritage.getSummary());
                heritages.add(item);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cityName", cityName);
        data.put("districtName", districtName);
        data.put("scenicCount", scenics.size());
        data.put("foodCount", foods.size());
        data.put("heritageCount", heritages.size());
        data.put("scenics", scenics);
        data.put("foods", foods);
        data.put("heritages", heritages);
        return Result.ok(data);
    }

    /**
     * 区县匹配：districtName 为空视为不过滤；否则要求精确相等
     */
    private boolean districtMatches(String districtName, String parsed) {
        if (!StringUtils.hasText(districtName)) {
            return true;
        }
        return districtName.equals(parsed);
    }

    /**
     * 从地址解析县级名称
     *
     * <p>取地址中所有「xx区/xx县/xx市」片段，跳过与市州同名的片段（如「成都市」），
     * 返回第一个剩余的片段，即为县级行政区名。</p>
     *
     * @param address  完整地址（如「四川省成都市武侯区武侯祠大街231号」）
     * @param cityName 市州短名（如「成都」）
     * @return 县级名称（如「武侯区」）；无法解析时返回 null
     */
    private String parseDistrict(String address, String cityName) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        // 定位市州名之后的片段，避免把「四川省成都市」这类前缀误判为县级名
        String tail = address;
        if (StringUtils.hasText(cityName)) {
            int idx = address.indexOf(cityName);
            if (idx >= 0) {
                tail = address.substring(idx + cityName.length());
            }
        }
        // 去掉紧邻的「市 / 州」后缀（如「成都市」之后从「市」开始的部分）
        tail = tail.replaceFirst("^[市州]", "");
        Matcher matcher = DISTRICT_PATTERN.matcher(tail);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
