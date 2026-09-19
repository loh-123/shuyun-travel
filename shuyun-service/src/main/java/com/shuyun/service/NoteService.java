package com.shuyun.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shuyun.common.PageResult;
import com.shuyun.pojo.dto.NoteSaveDTO;
import com.shuyun.pojo.entity.UserNote;

import java.util.Map;

/**
 * 旅行笔记业务接口
 */
public interface NoteService extends IService<UserNote> {

    /**
     * 分页查询笔记（标题 / 正文 / 标签命中关键词）
     *
     * @param userId  用户ID
     * @param keyword 关键词（可选）
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<UserNote> pageNote(Long userId, String keyword, int page, int size);

    /**
     * 新建或更新笔记
     *
     * @param userId 用户ID
     * @param noteId 笔记ID（为空表示新建）
     * @param dto    笔记内容
     * @return 笔记ID
     */
    Long saveNote(Long userId, Long noteId, NoteSaveDTO dto);

    /**
     * 删除笔记
     *
     * @param userId 用户ID
     * @param noteId 笔记ID
     */
    void removeNote(Long userId, Long noteId);

    /**
     * 统计：笔记篇数与累计字数
     *
     * @param userId 用户ID
     * @return {count, chars}
     */
    Map<String, Object> summary(Long userId);
}
