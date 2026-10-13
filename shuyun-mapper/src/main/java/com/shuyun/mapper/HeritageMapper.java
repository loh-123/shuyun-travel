package com.shuyun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shuyun.pojo.entity.Heritage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 非物质文化遗产 Mapper
 *
 * <p>继承 MyBatis-Plus 的 {@link BaseMapper}，自带单表 CRUD 能力。</p>
 */
@Mapper
public interface HeritageMapper extends BaseMapper<Heritage> {
}
