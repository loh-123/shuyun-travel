package com.shuyun.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.mapper.UserNoteMapper;
import com.shuyun.pojo.dto.NoteSaveDTO;
import com.shuyun.pojo.entity.UserNote;
import com.shuyun.service.NoteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 旅行笔记业务实现
 */
@Service
public class NoteServiceImpl extends ServiceImpl<UserNoteMapper, UserNote> implements NoteService {

    @Override
    public PageResult<UserNote> pageNote(Long userId, String keyword, int page, int size) {
        LambdaQueryWrapper<UserNote> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(UserNote::getUserId, userId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(UserNote::getTitle, keyword)
                    .or()
                    .like(UserNote::getContent, keyword)
                    .or()
                    .like(UserNote::getTags, keyword));
        }
        wrapper.orderByDesc(UserNote::getUpdateTime);

        Page<UserNote> result = page(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveNote(Long userId, Long noteId, NoteSaveDTO dto) {
        if (!StringUtils.hasText(dto.getTitle()) && !StringUtils.hasText(dto.getContent())) {
            throw new BusinessException("标题和内容不能同时为空");
        }

        UserNote note;
        if (noteId == null) {
            note = new UserNote();
            note.setUserId(userId);
        } else {
            note = getOne(Wrappers.<UserNote>lambdaQuery()
                    .eq(UserNote::getId, noteId)
                    .eq(UserNote::getUserId, userId));
            if (note == null) {
                throw new BusinessException("笔记不存在或无权修改");
            }
        }

        note.setTitle(dto.getTitle());
        note.setContent(dto.getContent());
        note.setTargetType(dto.getTargetType());
        note.setTargetId(dto.getTargetId());
        note.setTargetName(dto.getTargetName());
        note.setTags(dto.getTags());

        if (noteId == null) {
            save(note);
        } else {
            updateById(note);
        }
        return note.getId();
    }

    @Override
    public void removeNote(Long userId, Long noteId) {
        remove(Wrappers.<UserNote>lambdaQuery()
                .eq(UserNote::getId, noteId)
                .eq(UserNote::getUserId, userId));
    }

    @Override
    public Map<String, Object> summary(Long userId) {
        List<UserNote> notes = list(Wrappers.<UserNote>lambdaQuery().eq(UserNote::getUserId, userId));
        int chars = notes.stream()
                .mapToInt(n -> n.getContent() == null ? 0 : n.getContent().length())
                .sum();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", notes.size());
        data.put("chars", chars);
        return data;
    }
}
