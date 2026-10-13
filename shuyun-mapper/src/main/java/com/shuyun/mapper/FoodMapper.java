package com.shuyun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shuyun.pojo.entity.Food;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 美食 Mapper
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，自带单表 CRUD 能力；
 * 分类下拉通过 {@link #selectDistinctCategories()} 动态查询。</p>
 */
@Mapper
public interface FoodMapper extends BaseMapper<Food> {

    /**
     * 查询全部分类（去重，升序）
     *
     * @return 分类列表
     */
    @Select("SELECT DISTINCT category FROM food WHERE deleted = 0 AND category IS NOT NULL ORDER BY category")
    List<String> selectDistinctCategories();
}
