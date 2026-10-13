package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.entity.Food;
import com.shuyun.pojo.vo.FoodDetailVO;
import com.shuyun.pojo.vo.FoodVO;

import java.util.List;

/**
 * 美食业务接口
 */
public interface FoodService extends IService<Food> {

    /**
     * 美食分页查询
     *
     * @param page     页码，从 1 开始
     * @param size     每页大小
     * @param category 分类（可选）
     * @param cityName 所属城市（可选）
     * @param keyword  关键词，匹配名称 / 简介（可选）
     * @return 分页结果
     */
    PageResult<FoodVO> listFood(int page, int size, String category, String cityName, String keyword);

    /**
     * 美食详情
     *
     * @param id 美食ID
     * @return 美食详情
     */
    FoodDetailVO getFoodDetail(Long id);

    /**
     * 查询全部分类（去重，动态）
     *
     * @return 分类列表
     */
    List<String> listCategories();
}
