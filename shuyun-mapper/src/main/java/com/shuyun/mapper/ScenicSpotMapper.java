package com.shuyun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shuyun.pojo.entity.ScenicSpot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 景点 Mapper
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，自带单表 CRUD 能力。</p>
 */
@Mapper
public interface ScenicSpotMapper extends BaseMapper<ScenicSpot> {

    /**
     * 查询景点表中出现过的市州ID（去重）
     *
     * <p>库中景点表只有 city_id，市州名通过 city 表派生；
     * 此方法用于动态生成「城市下拉」数据源，不写死。</p>
     *
     * @return 去重后的市州ID列表
     */
    @Select("SELECT DISTINCT city_id FROM scenic_spot WHERE deleted = 0 AND city_id IS NOT NULL")
    List<Long> selectDistinctCityIds();
}
