package com.shuyun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shuyun.pojo.entity.UserViewHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 浏览足迹 Mapper
 */
@Mapper
public interface UserViewHistoryMapper extends BaseMapper<UserViewHistory> {
}
