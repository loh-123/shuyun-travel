package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.pojo.entity.CulturePerson;
import com.shuyun.pojo.vo.CulturePersonDetailVO;
import com.shuyun.pojo.vo.CulturePersonVO;

import java.util.List;

/**
 * 传承人业务接口
 */
public interface CulturePersonService extends IService<CulturePerson> {

    /**
     * 按文化ID查询传承人列表
     *
     * @param cultureId 文化ID
     * @return 传承人列表（简介截断到 60 字）
     */
    List<CulturePersonVO> listByCultureId(Long cultureId);

    /**
     * 查询传承人详情
     *
     * @param id 传承人ID
     * @return 传承人详情（简介全文 + 代表文化 + 视频）
     */
    CulturePersonDetailVO getDetail(Long id);

    /**
     * 查询全部传承人（限量）
     *
     * @param limit 返回条数上限
     * @return 传承人列表（简介截断到 60 字）
     */
    List<CulturePersonVO> listAll(int limit);
}
