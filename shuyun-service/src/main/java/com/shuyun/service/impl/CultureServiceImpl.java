package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.mapper.CityMapper;
import com.shuyun.mapper.CultureMapper;
import com.shuyun.mapper.ScenicSpotMapper;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.vo.CultureDetailVO;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.CultureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文化业务实现
 */
@Service
@RequiredArgsConstructor
public class CultureServiceImpl extends ServiceImpl<CultureMapper, Culture> implements CultureService {

    /** 详情页最多展示的关联景点数 */
    private static final int RELATED_LIMIT = 3;

    private final ScenicSpotMapper scenicSpotMapper;

    private final CityMapper cityMapper;

    @Override
    public List<Culture> listAll() {
        return list(Wrappers.<Culture>lambdaQuery().orderByAsc(Culture::getId));
    }

    @Override
    public CultureDetailVO getDetail(Long id) {
        Culture culture = getById(id);
        if (culture == null) {
            throw new BusinessException("文化条目不存在");
        }

        CultureDetailVO vo = new CultureDetailVO();
        BeanUtils.copyProperties(culture, vo);
        vo.setRelatedScenics(resolveRelatedScenics(culture.getRelatedScenicIds()));
        vo.setInheritors(parseInheritors(culture.getInheritorText()));
        return vo;
    }

    @Override
    public List<Culture> listByCategory(String category) {
        return list(Wrappers.<Culture>lambdaQuery()
                .eq(Culture::getCategory, category)
                .orderByAsc(Culture::getId));
    }

    /**
     * 解析关联景点ID（逗号分隔），按配置顺序返回，最多 {@value RELATED_LIMIT} 条
     */
    private List<ScenicSpotVO> resolveRelatedScenics(String ids) {
        if (!StringUtils.hasText(ids)) {
            return Collections.emptyList();
        }
        Map<Long, String> cityNames = cityMapper.selectList(null).stream()
                .collect(Collectors.toMap(City::getId, City::getName, (a, b) -> a));

        List<ScenicSpotVO> result = new ArrayList<>();
        for (String raw : ids.split(",")) {
            String text = raw.trim();
            if (text.isEmpty()) {
                continue;
            }
            ScenicSpot spot;
            try {
                spot = scenicSpotMapper.selectById(Long.valueOf(text));
            } catch (NumberFormatException e) {
                continue;
            }
            if (spot == null) {
                continue;
            }
            ScenicSpotVO item = new ScenicSpotVO();
            BeanUtils.copyProperties(spot, item);
            item.setCityName(cityNames.get(spot.getCityId()));
            result.add(item);
            if (result.size() >= RELATED_LIMIT) {
                break;
            }
        }
        return result;
    }

    /**
     * 解析传承人文本：每行一条「姓名|身份|一句话」
     */
    private List<Map<String, String>> parseInheritors(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<Map<String, String>> list = new ArrayList<>();
        for (String line : text.split("\\n")) {
            String[] parts = line.trim().split("\\|");
            if (parts.length < 2 || !StringUtils.hasText(parts[0])) {
                continue;
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", parts[0].trim());
            item.put("title", parts[1].trim());
            item.put("intro", parts.length > 2 ? parts[2].trim() : "");
            list.add(item);
        }
        return list;
    }
}
