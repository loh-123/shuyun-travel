package com.shuyun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shuyun.pojo.entity.UserNote;
import org.apache.ibatis.annotations.Mapper;

/**
 * 旅行笔记 Mapper
 */
@Mapper
public interface UserNoteMapper extends BaseMapper<UserNote> {
}
