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
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.ScenicImage;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.vo.ScenicDetailVO;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.ScenicService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 景点业务实现
 */
@Service
@RequiredArgsConstructor
public class ScenicServiceImpl extends ServiceImpl<ScenicSpotMapper, ScenicSpot> implements ScenicService {

    /** 热门景点数量 */
    private static final int HOT_SIZE = 6;

    /** 同城推荐数量 */
    private static final int RECOMMEND_SIZE = 3;

    /** 排序值：人气（按浏览次数） */
    private static final String SORT_HOT = "hot";

    private final CityMapper cityMapper;

    private final ScenicImageMapper scenicImageMapper;

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
    public ScenicDetailVO getDetail(Long id) {
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

        vo.setImages(scenicImageMapper.selectList(Wrappers.<ScenicImage>lambdaQuery()
                .eq(ScenicImage::getScenicId, id)
                .orderByAsc(ScenicImage::getSortOrder)));

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
    public List<ScenicSpotVO> listHot() {
        Map<Long, String> cityNames = cityNameMap();
        return list(Wrappers.<ScenicSpot>lambdaQuery()
                .orderByDesc(ScenicSpot::getScore)
                .last("limit " + HOT_SIZE))
                .stream()
                .map(spot -> toVO(spot, cityNames))
                .collect(Collectors.toList());
    }

    @Override
    public List<City> listCities() {
        return cityMapper.selectList(Wrappers.<City>lambdaQuery().orderByAsc(City::getId));
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
}
