package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.entity.Heritage;
import com.shuyun.pojo.vo.HeritageDetailVO;
import com.shuyun.pojo.vo.HeritageVO;

/**
 * 非物质文化遗产业务接口
 */
public interface HeritageService extends IService<Heritage> {

    /**
     * 非遗分页查询
     *
     * @param page     页码，从 1 开始
     * @param size     每页大小
     * @param category 类别（可选）
     * @param level    级别（可选）
     * @param keyword  关键词，匹配名称 / 简介（可选）
     * @return 分页结果
     */
    PageResult<HeritageVO> listHeritage(int page, int size, String category, String level, String keyword);

    /**
     * 非遗详情（含图片集）
     *
     * @param id 非遗ID
     * @return 非遗详情
     */
    HeritageDetailVO getHeritageDetail(Long id);
}
