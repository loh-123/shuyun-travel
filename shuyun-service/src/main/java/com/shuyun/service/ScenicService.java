package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.entity.ScenicSpot;
import com.shuyun.pojo.vo.ScenicDetailVO;
import com.shuyun.pojo.vo.ScenicSpotVO;

import java.util.List;

/**
 * 景点业务接口
 */
public interface ScenicService extends IService<ScenicSpot> {

    /**
     * 分页查询景点，支持按城市/类型/关键词筛选
     *
     * @param page    页码，从 1 开始
     * @param size    每页大小
     * @param cityId  市州ID（可选）
     * @param type    景点类型（可选）
     * @param keyword 关键词，匹配名称或标签（可选）
     * @return 分页结果
     */
    PageResult<ScenicSpotVO> pageScenic(int page, int size, Long cityId, String type, String keyword);

    /**
     * 景点详情：基础信息 + 图片列表 + 同城市推荐（3 条）
     *
     * @param id 景点ID
     * @return 景点详情
     */
    ScenicDetailVO getDetail(Long id);

    /**
     * 热门景点：评分最高的 6 个
     *
     * @return 景点列表
     */
    List<ScenicSpotVO> listHot();

    /**
     * 查询所有市州
     *
     * @return 市州列表
     */
    List<City> listCities();
}
