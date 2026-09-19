package com.shuyun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shuyun.pojo.entity.City;
import org.apache.ibatis.annotations.Mapper;

/**
 * 市州 Mapper
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，自带单表 CRUD 能力。</p>
 */
@Mapper
public interface CityMapper extends BaseMapper<City> {
}
