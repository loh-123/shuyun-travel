package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.CityMapper;
import com.shuyun.mapper.CultureImageMapper;
import com.shuyun.mapper.CultureMapper;
import com.shuyun.mapper.ScenicSpotMapper;
import com.shuyun.pojo.dto.CultureSaveDTO;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.pojo.entity.CultureImage;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.vo.AdminCultureVO;
import com.shuyun.pojo.vo.CultureDetailVO;
import com.shuyun.pojo.vo.CultureVO;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.CultureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final CultureImageMapper cultureImageMapper;

    @Override
    public List<Culture> listAll() {
        return list(Wrappers.<Culture>lambdaQuery().orderByAsc(Culture::getId));
    }

    @Override
    public List<CultureVO> listCulture() {
        return list(Wrappers.<Culture>lambdaQuery().orderByAsc(Culture::getId)).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public CultureDetailVO getCultureDetail(Long id) {
        Culture culture = getById(id);
        if (culture == null) {
            throw new BusinessException("文化条目不存在");
        }

        // 浏览即累加人气：SQL 原子自增，避免并发丢更新
        update(Wrappers.<Culture>lambdaUpdate()
                .setSql("view_count = view_count + 1")
                .eq(Culture::getId, id));

        CultureDetailVO vo = new CultureDetailVO();
        BeanUtils.copyProperties(culture, vo);
        // 图片集：按 sort_order 升序，仅返回图片地址（库中已是 /images/culture/... 形式）
        vo.setImages(cultureImageMapper.selectList(Wrappers.<CultureImage>lambdaQuery()
                        .eq(CultureImage::getCultureId, id)
                        .orderByAsc(CultureImage::getSortOrder))
                .stream()
                .map(CultureImage::getImageUrl)
                .collect(Collectors.toList()));
        vo.setRelatedScenics(resolveRelatedScenics(culture.getRelatedScenicIds()));
        vo.setInheritors(parseInheritors(culture.getInheritorText()));
        return vo;
    }

    @Override
    public List<CultureVO> listByCategory(String category) {
        return list(Wrappers.<Culture>lambdaQuery()
                .eq(Culture::getCategory, category)
                .orderByAsc(Culture::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 文化实体转前台列表项 VO
     */
    private CultureVO toVO(Culture culture) {
        CultureVO vo = new CultureVO();
        BeanUtils.copyProperties(culture, vo);
        return vo;
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

    @Override
    public PageResult<AdminCultureVO> pageAdminCulture(int page, int size, String keyword, String category) {
        LambdaQueryWrapper<Culture> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Culture::getDeleted, 0)
                .eq(StringUtils.hasText(category), Culture::getCategory, category);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Culture::getName, keyword).or().like(Culture::getTags, keyword));
        }
        wrapper.orderByDesc(Culture::getCreateTime);

        Page<Culture> result = page(new Page<>(page, size), wrapper);
        List<AdminCultureVO> records = result.getRecords().stream().map(culture -> {
            AdminCultureVO vo = new AdminCultureVO();
            BeanUtils.copyProperties(culture, vo);
            vo.setRelatedScenicCount(countRelatedScenics(culture.getRelatedScenicIds()));
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), records, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCulture(CultureSaveDTO dto) {
        Culture culture = new Culture();
        BeanUtils.copyProperties(dto, culture);
        culture.setId(null);
        culture.setViewCount(0);
        culture.setDeleted(0);
        save(culture);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCulture(Long id, CultureSaveDTO dto) {
        Culture culture = getById(id);
        if (culture == null || Integer.valueOf(1).equals(culture.getDeleted())) {
            throw new BusinessException("文化条目不存在");
        }
        Culture update = new Culture();
        BeanUtils.copyProperties(dto, update);
        update.setId(id);
        updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeCulture(Long id) {
        update(Wrappers.<Culture>lambdaUpdate().set(Culture::getDeleted, 1).eq(Culture::getId, id));
    }

    /**
     * 统计关联景点数量（related_scenic_ids 为逗号分隔）
     */
    private int countRelatedScenics(String ids) {
        if (!StringUtils.hasText(ids)) {
            return 0;
        }
        int count = 0;
        for (String raw : ids.split(",")) {
            if (StringUtils.hasText(raw)) {
                count++;
            }
        }
        return count;
    }
}
