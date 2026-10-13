package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.mapper.CulturePersonMapper;
import com.shuyun.pojo.entity.CulturePerson;
import com.shuyun.pojo.vo.CulturePersonDetailVO;
import com.shuyun.pojo.vo.CulturePersonVO;
import com.shuyun.service.CulturePersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 传承人业务实现
 */
@Service
@RequiredArgsConstructor
public class CulturePersonServiceImpl
        extends ServiceImpl<CulturePersonMapper, CulturePerson>
        implements CulturePersonService {

    /** 列表卡片简介截断长度 */
    private static final int BRIEF_LIMIT = 60;

    @Override
    public List<CulturePersonVO> listByCultureId(Long cultureId) {
        if (cultureId == null) {
            return Collections.emptyList();
        }
        return list(Wrappers.<CulturePerson>lambdaQuery()
                .eq(CulturePerson::getDeleted, 0)
                .eq(CulturePerson::getCultureId, cultureId)
                .orderByAsc(CulturePerson::getSortOrder)
                .orderByAsc(CulturePerson::getId))
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public CulturePersonDetailVO getDetail(Long id) {
        CulturePerson person = getById(id);
        if (person == null || Integer.valueOf(1).equals(person.getDeleted())) {
            throw new BusinessException("传承人不存在");
        }
        CulturePersonDetailVO vo = new CulturePersonDetailVO();
        BeanUtils.copyProperties(person, vo);
        return vo;
    }

    @Override
    public List<CulturePersonVO> listAll(int limit) {
        int size = limit > 0 ? limit : 20;
        Page<CulturePerson> result = page(new Page<>(1, size),
                Wrappers.<CulturePerson>lambdaQuery()
                        .eq(CulturePerson::getDeleted, 0)
                        .orderByAsc(CulturePerson::getSortOrder)
                        .orderByAsc(CulturePerson::getId));
        return result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 实体转列表项 VO（简介截断到 {@value BRIEF_LIMIT} 字）
     */
    private CulturePersonVO toVO(CulturePerson person) {
        CulturePersonVO vo = new CulturePersonVO();
        BeanUtils.copyProperties(person, vo);
        vo.setBrief(truncate(person.getBrief()));
        return vo;
    }

    /**
     * 简介截断：去除换行，超过 {@value BRIEF_LIMIT} 字补省略号
     */
    private String truncate(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String plain = text.replace("\n", "").replace("\r", "").trim();
        return plain.length() > BRIEF_LIMIT ? plain.substring(0, BRIEF_LIMIT) + "…" : plain;
    }
}
