package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.HeritageImageMapper;
import com.shuyun.mapper.HeritageMapper;
import com.shuyun.pojo.entity.Heritage;
import com.shuyun.pojo.entity.HeritageImage;
import com.shuyun.pojo.vo.HeritageDetailVO;
import com.shuyun.pojo.vo.HeritageVO;
import com.shuyun.service.HeritageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 非物质文化遗产业务实现
 */
@Service
@RequiredArgsConstructor
public class HeritageServiceImpl extends ServiceImpl<HeritageMapper, Heritage> implements HeritageService {

    private final HeritageImageMapper heritageImageMapper;

    @Override
    public PageResult<HeritageVO> listHeritage(int page, int size, String category, String level, String keyword) {
        LambdaQueryWrapper<Heritage> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(Heritage::getDeleted, 0)
                .eq(StringUtils.hasText(category), Heritage::getCategory, category)
                .eq(StringUtils.hasText(level), Heritage::getLevel, level);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Heritage::getName, keyword).or().like(Heritage::getSummary, keyword));
        }
        wrapper.orderByAsc(Heritage::getId);

        Page<Heritage> result = page(new Page<>(page, size), wrapper);
        List<Heritage> list = result.getRecords();
        List<HeritageVO> records = list.stream().map(this::toVO).collect(Collectors.toList());

        // 封面图：批量取每项 sort_order 最小的图片（列表卡片展示真实图片）
        if (!records.isEmpty()) {
            List<Long> ids = list.stream().map(Heritage::getId).collect(Collectors.toList());
            List<HeritageImage> images = heritageImageMapper.selectList(Wrappers.<HeritageImage>lambdaQuery()
                    .in(HeritageImage::getHeritageId, ids)
                    .orderByAsc(HeritageImage::getHeritageId)
                    .orderByAsc(HeritageImage::getSortOrder));
            Map<String, String> coverMap = new HashMap<>();
            for (HeritageImage img : images) {
                coverMap.putIfAbsent(String.valueOf(img.getHeritageId()), img.getImageUrl());
            }
            records.forEach(vo -> vo.setCoverImage(coverMap.get(String.valueOf(vo.getId()))));
        }

        return PageResult.of(result.getTotal(), records, page, size);
    }

    @Override
    public HeritageDetailVO getHeritageDetail(Long id) {
        Heritage heritage = getById(id);
        if (heritage == null) {
            throw new BusinessException("非遗项目不存在");
        }
        HeritageDetailVO vo = new HeritageDetailVO();
        BeanUtils.copyProperties(heritage, vo);
        // 图片集：按 sort_order 升序，仅返回图片地址（库中已是 /images/heritage/... 形式）
        vo.setImages(heritageImageMapper.selectList(Wrappers.<HeritageImage>lambdaQuery()
                        .eq(HeritageImage::getHeritageId, id)
                        .orderByAsc(HeritageImage::getSortOrder))
                .stream()
                .map(HeritageImage::getImageUrl)
                .collect(Collectors.toList()));
        return vo;
    }

    /**
     * 非遗实体转列表项 VO
     */
    private HeritageVO toVO(Heritage heritage) {
        HeritageVO vo = new HeritageVO();
        BeanUtils.copyProperties(heritage, vo);
        return vo;
    }
}
