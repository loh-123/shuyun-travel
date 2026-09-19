package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.dto.NoteSaveDTO;
import com.shuyun.pojo.entity.UserNote;
import com.shuyun.service.NoteService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 旅行笔记接口（需登录）
 */
@RestController
@RequestMapping("/api/user/note")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * 分页查询笔记
     *
     * @param keyword 关键词（标题 / 正文 / 标签，可选）
     * @param page    页码，默认 1
     * @param size    每页大小，默认 12
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<UserNote>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size) {
        return Result.ok(noteService.pageNote(UserContext.getUserId(), keyword, page, size));
    }

    /**
     * 统计：篇数与累计字数
     *
     * @return {count, chars}
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary() {
        return Result.ok(noteService.summary(UserContext.getUserId()));
    }

    /**
     * 笔记详情
     *
     * @param id 笔记ID
     * @return 笔记
     */
    @GetMapping("/{id}")
    public Result<UserNote> detail(@PathVariable Long id) {
        return Result.ok(noteService.getById(id));
    }

    /**
     * 新建笔记
     *
     * @param dto 笔记内容
     * @return 新笔记ID
     */
    @PostMapping
    public Result<Long> create(@RequestBody NoteSaveDTO dto) {
        return Result.ok(noteService.saveNote(UserContext.getUserId(), null, dto));
    }

    /**
     * 更新笔记
     *
     * @param id  笔记ID
     * @param dto 笔记内容
     * @return 笔记ID
     */
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @RequestBody NoteSaveDTO dto) {
        return Result.ok(noteService.saveNote(UserContext.getUserId(), id, dto));
    }

    /**
     * 删除笔记
     *
     * @param id 笔记ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        noteService.removeNote(UserContext.getUserId(), id);
        return Result.ok();
    }
}
