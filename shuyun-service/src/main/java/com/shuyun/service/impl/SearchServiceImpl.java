package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shuyun.mapper.CityMapper;
import com.shuyun.mapper.CultureMapper;
import com.shuyun.mapper.ScenicSpotMapper;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聚合搜索业务实现
 *
 * <p>排序策略：名称精准命中 &gt; 名称包含 &gt; 简介/地址/标签/正文命中，同档按评分（景点）或 ID。</p>
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    /** 热门搜索词（搜索框空态展示，均已验证能搜出结果） */
    private static final List<String> HOT_KEYWORDS =
            List.of("九寨沟", "三星堆", "大熊猫", "都江堰", "成都", "川剧", "火锅");

    /** 市州最多返回条数 */
    private static final int CITY_LIMIT = 3;

    private final ScenicSpotMapper scenicSpotMapper;

    private final CultureMapper cultureMapper;

    private final CityMapper cityMapper;

    @Override
    public Map<String, Object> search(String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        int max = limit <= 0 ? 6 : Math.min(limit, 20);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", kw);
        data.put("hotKeywords", HOT_KEYWORDS);

        if (!StringUtils.hasText(kw)) {
            data.put("cities", Collections.emptyList());
            data.put("scenics", Collections.emptyList());
            data.put("cultures", Collections.emptyList());
            return data;
        }

        data.put("cities", searchCities(kw));
        data.put("scenics", searchScenics(kw, max));
        data.put("cultures", searchCultures(kw, max));
        return data;
    }

    /**
     * 市州：名称包含关键词，并附带该市州的景点数
     */
    private List<Map<String, Object>> searchCities(String kw) {
        List<City> cities = cityMapper.selectList(Wrappers.<City>lambdaQuery()
                .like(City::getName, kw)
                .orderByAsc(City::getId)
                .last("limit " + CITY_LIMIT));

        List<Map<String, Object>> result = new ArrayList<>();
        for (City city : cities) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", city.getId());
            item.put("name", city.getName());
            item.put("count", scenicSpotMapper.selectCount(
                    Wrappers.<ScenicSpot>lambdaQuery().eq(ScenicSpot::getCityId, city.getId())));
            result.add(item);
        }
        return result;
    }

    /**
     * 景点：名称 / 简介 / 标签 / 地址命中
     */
    private List<ScenicSpotVO> searchScenics(String kw, int limit) {
        List<ScenicSpot> list = scenicSpotMapper.selectList(Wrappers.<ScenicSpot>lambdaQuery()
                .and(w -> w.like(ScenicSpot::getName, kw)
                        .or().like(ScenicSpot::getDescription, kw)
                        .or().like(ScenicSpot::getTags, kw)
                        .or().like(ScenicSpot::getAddress, kw))
                .orderByDesc(ScenicSpot::getScore)
                .last("limit " + (limit * 2)));

        Map<Long, String> cityNames = cityMapper.selectList(null).stream()
                .collect(Collectors.toMap(City::getId, City::getName, (a, b) -> a));

        return list.stream()
                .sorted(Comparator.comparingInt((ScenicSpot s) -> rank(s.getName(), kw)))
                .limit(limit)
                .map(s -> {
                    ScenicSpotVO vo = new ScenicSpotVO();
                    BeanUtils.copyProperties(s, vo);
                    vo.setCityName(cityNames.get(s.getCityId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 文化：名称 / 简介 / 标签 / 正文命中（返回前清空正文，避免响应体过大）
     */
    private List<Culture> searchCultures(String kw, int limit) {
        List<Culture> list = cultureMapper.selectList(Wrappers.<Culture>lambdaQuery()
                .and(w -> w.like(Culture::getName, kw)
                        .or().like(Culture::getSummary, kw)
                        .or().like(Culture::getTags, kw)
                        .or().like(Culture::getContent, kw))
                .last("limit " + (limit * 2)));

        return list.stream()
                .sorted(Comparator.comparingInt((Culture c) -> rank(c.getName(), kw)))
                .limit(limit)
                .map(c -> {
                    c.setContent(null);
                    c.setInheritorText(null);
                    return c;
                })
                .collect(Collectors.toList());
    }

    /**
     * 相关度：名称精准命中(0) &gt; 名称包含(1) &gt; 其它字段命中(2)
     */
    private int rank(String name, String kw) {
        if (name == null) {
            return 2;
        }
        if (name.equals(kw)) {
            return 0;
        }
        return name.contains(kw) ? 1 : 2;
    }
}
