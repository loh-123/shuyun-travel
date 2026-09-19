package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.pojo.entity.Culture;
import com.shuyun.pojo.vo.CultureDetailVO;

import java.util.List;

/**
 * 文化业务接口
 */
public interface CultureService extends IService<Culture> {

    /**
     * 查询全部文化条目（不分页）
     *
     * @return 文化列表
     */
    List<Culture> listAll();

    /**
     * 查询文化详情（含关联景点与传承人）
     *
     * @param id 文化ID
     * @return 文化详情
     */
    CultureDetailVO getDetail(Long id);

    /**
     * 按分类查询文化条目
     *
     * @param category 分类
     * @return 文化列表
     */
    List<Culture> listByCategory(String category);
}
