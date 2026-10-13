package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.dto.CultureSaveDTO;
import com.shuyun.pojo.vo.AdminCultureVO;
import com.shuyun.service.CultureService;
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

/**
 * 文化管理接口（后台，仅管理员）
 *
 * <p>权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/culture")
@RequiredArgsConstructor
public class AdminCultureController {

    private final CultureService cultureService;

    /**
     * 文化分页列表（含相关景点数）
     *
     * @param keyword  关键词（名称 / 标签，可选）
     * @param category 分类（可选）
     * @param page     页码，默认 1
     * @param size     每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<AdminCultureVO>> list(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String category,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(cultureService.pageAdminCulture(page, size, keyword, category));
    }

    /**
     * 新增文化条目
     *
     * @param dto 文化入参
     * @return 空结果
     */
    @PostMapping
    public Result<Void> create(@RequestBody CultureSaveDTO dto) {
        cultureService.saveCulture(dto);
        return Result.ok();
    }

    /**
     * 编辑文化条目
     *
     * @param id  文化ID
     * @param dto 文化入参
     * @return 空结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CultureSaveDTO dto) {
        cultureService.updateCulture(id, dto);
        return Result.ok();
    }

    /**
     * 删除文化条目（软删除）
     *
     * @param id 文化ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cultureService.removeCulture(id);
        return Result.ok();
    }
}
