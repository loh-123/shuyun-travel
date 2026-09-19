package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.mapper.CultureMapper;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.service.CultureService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文化业务实现
 */
@Service
public class CultureServiceImpl extends ServiceImpl<CultureMapper, Culture> implements CultureService {

    @Override
    public List<Culture> listAll() {
        return list(Wrappers.<Culture>lambdaQuery().orderByAsc(Culture::getId));
    }

    @Override
    public Culture getDetail(Long id) {
        Culture culture = getById(id);
        if (culture == null) {
            throw new BusinessException("文化条目不存在");
        }
        return culture;
    }

    @Override
    public List<Culture> listByCategory(String category) {
        return list(Wrappers.<Culture>lambdaQuery()
                .eq(Culture::getCategory, category)
                .orderByAsc(Culture::getId));
    }
}
