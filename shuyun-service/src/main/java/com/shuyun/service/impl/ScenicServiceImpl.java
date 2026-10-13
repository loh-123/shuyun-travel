package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.CityMapper;
import com.shuyun.mapper.ScenicImageMapper;
import com.shuyun.mapper.ScenicSpotMapper;
import com.shuyun.mapper.UserCollectionMapper;
import com.shuyun.pojo.dto.ScenicSaveDTO;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.ScenicImage;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.entity.UserCollection;
import com.shuyun.pojo.vo.AdminScenicVO;
import com.shuyun.pojo.vo.ScenicDetailVO;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.ScenicService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 景点业务实现
 */
@Service
@RequiredArgsConstructor
public class ScenicServiceImpl extends ServiceImpl<ScenicSpotMapper, ScenicSpot> implements ScenicService {

    /** 热门景点默认数量 */
    private static final int HOT_SIZE = 6;

    /** 同城推荐数量 */
    private static final int RECOMMEND_SIZE = 3;

    /** 排序值：人气（按浏览次数） */
    private static final String SORT_HOT = "hot";

    private final CityMapper cityMapper;

    private final ScenicImageMapper scenicImageMapper;

    private final UserCollectionMapper userCollectionMapper;

    @Override
    public PageResult<ScenicSpotVO> listScenic(int page, int size, Long cityId, String type, String keyword) {
        return pageScenic(page, size, cityId, type, keyword, "default");
    }

    @Override
    public PageResult<ScenicSpotVO> pageScenic(int page, int size, Long cityId, String type, String keyword,
                                               String sort) {
        LambdaQueryWrapper<ScenicSpot> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(cityId != null, ScenicSpot::getCityId, cityId)
                .eq(StringUtils.hasText(type), ScenicSpot::getType, type);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ScenicSpot::getName, keyword)
                    .or()
                    .like(ScenicSpot::getTags, keyword));
        }
        // 排序：hot = 人气（浏览量）优先；default/score = 评分优先，人气次之
        if (SORT_HOT.equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(ScenicSpot::getViewCount).orderByDesc(ScenicSpot::getScore);
        } else {
            wrapper.orderByDesc(ScenicSpot::getScore).orderByDesc(ScenicSpot::getViewCount);
        }

        Page<ScenicSpot> result = page(new Page<>(page, size), wrapper);
        Map<Long, String> cityNames = cityNameMap();
        List<ScenicSpotVO> records = result.getRecords().stream()
                .map(spot -> toVO(spot, cityNames))
                .collect(Collectors.toList());
        return PageResult.of(result.getTotal(), records, page, size);
    }

    @Override
    public ScenicDetailVO getScenicDetail(Long id) {
        ScenicSpot spot = getById(id);
        if (spot == null) {
            throw new BusinessException("景点不存在");
        }

        // 浏览即累加人气：用 SQL 原子自增，避免并发下丢更新
        update(Wrappers.<ScenicSpot>lambdaUpdate()
                .setSql("view_count = view_count + 1")
                .eq(ScenicSpot::getId, id));

        ScenicDetailVO vo = new ScenicDetailVO();
        BeanUtils.copyProperties(spot, vo);
        // 返回给前端的浏览量已包含刚刚这一次
        vo.setViewCount((spot.getViewCount() == null ? 0 : spot.getViewCount()) + 1);
        City city = spot.getCityId() == null ? null : cityMapper.selectById(spot.getCityId());
        vo.setCityName(city == null ? null : city.getName());
        // 景点视频链接（可为空，前端据此决定是否渲染"相关影像"区块）
        vo.setVideoUrl(spot.getVideoUrl());

        // 详情图：按 sort_order 升序，仅返回图片地址（库中已是 /images/scenic/... 形式）
        vo.setImages(scenicImageMapper.selectList(Wrappers.<ScenicImage>lambdaQuery()
                        .eq(ScenicImage::getScenicId, id)
                        .orderByAsc(ScenicImage::getSortOrder))
                .stream()
                .map(ScenicImage::getImageUrl)
                .collect(Collectors.toList()));

        Map<Long, String> cityNames = cityNameMap();
        List<ScenicSpotVO> recommends = list(Wrappers.<ScenicSpot>lambdaQuery()
                .eq(spot.getCityId() != null, ScenicSpot::getCityId, spot.getCityId())
                .ne(ScenicSpot::getId, id)
                .orderByDesc(ScenicSpot::getScore)
                .last("limit " + RECOMMEND_SIZE))
                .stream()
                .map(s -> toVO(s, cityNames))
                .collect(Collectors.toList());
        vo.setRecommends(recommends);

        return vo;
    }

    @Override
    public List<ScenicSpotVO> getHotScenic(int limit) {
        int size = limit > 0 ? limit : HOT_SIZE;
        Map<Long, String> cityNames = cityNameMap();
        return list(Wrappers.<ScenicSpot>lambdaQuery()
                .orderByDesc(ScenicSpot::getScore)
                .last("limit " + size))
                .stream()
                .map(spot -> toVO(spot, cityNames))
                .collect(Collectors.toList());
    }

    @Override
    public List<City> listCities() {
        return cityMapper.selectList(Wrappers.<City>lambdaQuery().orderByAsc(City::getId));
    }

    @Override
    public List<String> listCityNames() {
        // 动态：先取景点表出现过的 DISTINCT city_id，再联 city 表派生名称（不写死）
        List<Long> cityIds = baseMapper.selectDistinctCityIds();
        if (cityIds == null || cityIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> names = cityNameMap();
        return cityIds.stream()
                .sorted()
                .map(names::get)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 加载「市州ID -> 市州名称」映射
     */
    private Map<Long, String> cityNameMap() {
        return cityMapper.selectList(null).stream()
                .collect(Collectors.toMap(City::getId, City::getName, (a, b) -> a));
    }

    /**
     * 实体转列表项 VO
     */
    private ScenicSpotVO toVO(ScenicSpot spot, Map<Long, String> cityNames) {
        ScenicSpotVO vo = new ScenicSpotVO();
        BeanUtils.copyProperties(spot, vo);
        vo.setCityName(cityNames.get(spot.getCityId()));
        return vo;
    }

    @Override
    public PageResult<AdminScenicVO> pageAdminScenic(int page, int size, String keyword, Long cityId, String type,
                                                     String level) {
        LambdaQueryWrapper<ScenicSpot> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ScenicSpot::getDeleted, 0)
                .eq(cityId != null, ScenicSpot::getCityId, cityId)
                .eq(StringUtils.hasText(type), ScenicSpot::getType, type)
                .eq(StringUtils.hasText(level), ScenicSpot::getLevel, level);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(ScenicSpot::getName, keyword).or().like(ScenicSpot::getTags, keyword));
        }
        wrapper.orderByDesc(ScenicSpot::getCreateTime);

        Page<ScenicSpot> result = page(new Page<>(page, size), wrapper);
        List<ScenicSpot> spots = result.getRecords();
        List<Long> ids = spots.stream().map(ScenicSpot::getId).collect(Collectors.toList());
        Map<Long, String> cityNames = cityNameMap();
        Map<Long, Integer> collectCounts = collectCountMap(ids);
        Map<Long, List<String>> imageMap = imageMap(ids);

        List<AdminScenicVO> records = spots.stream().map(spot -> {
            AdminScenicVO vo = new AdminScenicVO();
            BeanUtils.copyProperties(spot, vo);
            vo.setCityName(cityNames.get(spot.getCityId()));
            vo.setCollectCount(collectCounts.getOrDefault(spot.getId(), 0));
            vo.setImages(imageMap.getOrDefault(spot.getId(), Collections.emptyList()));
            return vo;
        }).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), records, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveScenic(ScenicSaveDTO dto) {
        ScenicSpot spot = new ScenicSpot();
        BeanUtils.copyProperties(dto, spot);
        spot.setId(null);
        spot.setViewCount(0);
        spot.setDeleted(0);
        save(spot);
        saveImages(spot.getId(), dto.getImages());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateScenic(Long id, ScenicSaveDTO dto) {
        ScenicSpot spot = getById(id);
        if (spot == null || Integer.valueOf(1).equals(spot.getDeleted())) {
            throw new BusinessException("景点不存在");
        }
        ScenicSpot update = new ScenicSpot();
        BeanUtils.copyProperties(dto, update);
        update.setId(id);
        updateById(update);
        // 详情图全量覆盖：先清空旧图，再写入新图
        scenicImageMapper.delete(Wrappers.<ScenicImage>lambdaQuery().eq(ScenicImage::getScenicId, id));
        saveImages(id, dto.getImages());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeScenic(Long id) {
        // 逻辑删除景点与其详情图
        update(Wrappers.<ScenicSpot>lambdaUpdate().set(ScenicSpot::getDeleted, 1).eq(ScenicSpot::getId, id));
        scenicImageMapper.update(null, Wrappers.<ScenicImage>lambdaUpdate()
                .set(ScenicImage::getDeleted, 1)
                .eq(ScenicImage::getScenicId, id));
    }

    /**
     * 批量写入景点详情图（按列表顺序设置排序号）
     */
    private void saveImages(Long scenicId, List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        int order = 0;
        for (String url : images) {
            if (!StringUtils.hasText(url)) {
                continue;
            }
            ScenicImage image = new ScenicImage();
            image.setScenicId(scenicId);
            image.setImageUrl(url);
            image.setSortOrder(order++);
            image.setDeleted(0);
            scenicImageMapper.insert(image);
        }
    }

    /**
     * 统计每个景点的收藏数
     */
    private Map<Long, Integer> collectCountMap(List<Long> scenicIds) {
        Map<Long, Integer> result = new HashMap<>();
        if (scenicIds.isEmpty()) {
            return result;
        }
        List<UserCollection> collections = userCollectionMapper.selectList(Wrappers.<UserCollection>lambdaQuery()
                .eq(UserCollection::getTargetType, "SCENIC")
                .in(UserCollection::getTargetId, scenicIds));
        for (UserCollection collection : collections) {
            result.merge(collection.getTargetId(), 1, Integer::sum);
        }
        return result;
    }

    /**
     * 批量加载景点详情图：景点ID -> 图片地址列表
     */
    private Map<Long, List<String>> imageMap(List<Long> scenicIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (scenicIds.isEmpty()) {
            return result;
        }
        List<ScenicImage> images = scenicImageMapper.selectList(Wrappers.<ScenicImage>lambdaQuery()
                .eq(ScenicImage::getDeleted, 0)
                .in(ScenicImage::getScenicId, scenicIds)
                .orderByAsc(ScenicImage::getSortOrder));
        for (ScenicImage image : images) {
            result.computeIfAbsent(image.getScenicId(), k -> new ArrayList<>()).add(image.getImageUrl());
        }
        return result;
    }
}
