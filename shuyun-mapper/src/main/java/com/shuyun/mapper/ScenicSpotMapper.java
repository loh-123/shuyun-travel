package com.shuyun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shuyun.pojo.entity.ScenicSpot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 景点 Mapper
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，自带单表 CRUD 能力。</p>
 */
@Mapper
public interface ScenicSpotMapper extends BaseMapper<ScenicSpot> {
}
