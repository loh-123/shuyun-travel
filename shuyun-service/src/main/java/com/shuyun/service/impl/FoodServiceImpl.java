package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.FoodMapper;
import com.shuyun.pojo.entity.Food;
import com.shuyun.pojo.vo.FoodDetailVO;
import com.shuyun.pojo.vo.FoodVO;
import com.shuyun.service.FoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 美食业务实现
 */
@Service
@RequiredArgsConstructor
public class FoodServiceImpl extends ServiceImpl<FoodMapper, Food> implements FoodService {

    @Override
    public PageResult<FoodVO> listFood(int page, int size, String category, String cityName, String keyword) {
        LambdaQueryWrapper<Food> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Food::getDeleted, 0)
                .eq(StringUtils.hasText(category), Food::getCategory, category)
                .eq(StringUtils.hasText(cityName), Food::getCityName, cityName);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Food::getName, keyword).or().like(Food::getDescription, keyword));
        }
        // 推荐指数高的优先，其次按主键
        wrapper.orderByDesc(Food::getRecommendIndex).orderByDesc(Food::getId);

        Page<Food> result = page(new Page<>(page, size), wrapper);
        List<FoodVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PageResult.of(result.getTotal(), records, page, size);
    }

    @Override
    public FoodDetailVO getFoodDetail(Long id) {
        Food food = getById(id);
        if (food == null) {
            throw new BusinessException("美食不存在");
        }
        FoodDetailVO vo = new FoodDetailVO();
        BeanUtils.copyProperties(food, vo);
        return vo;
    }

    @Override
    public List<String> listCategories() {
        return baseMapper.selectDistinctCategories();
    }

    /**
     * 美食实体转列表项 VO
     */
    private FoodVO toVO(Food food) {
        FoodVO vo = new FoodVO();
        BeanUtils.copyProperties(food, vo);
        return vo;
    }
}
