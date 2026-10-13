package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.dto.ScenicSaveDTO;
import com.shuyun.pojo.vo.AdminScenicVO;
import com.shuyun.service.ScenicService;
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
 * 景点管理接口（后台，仅管理员）
 *
 * <p>权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/scenic")
@RequiredArgsConstructor
public class AdminScenicController {

    private final ScenicService scenicService;

    /**
     * 景点分页列表（含市州名 / 收藏数 / 详情图）
     *
     * @param keyword 关键词（名称 / 标签，可选）
     * @param cityId  市州ID（可选）
     * @param type    景点类型（可选）
     * @param level   景区等级（可选）
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<AdminScenicVO>> list(@RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) Long cityId,
                                                  @RequestParam(required = false) String type,
                                                  @RequestParam(required = false) String level,
                                                  @RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(scenicService.pageAdminScenic(page, size, keyword, cityId, type, level));
    }

    /**
     * 新增景点
     *
     * @param dto 景点入参
     * @return 空结果
     */
    @PostMapping
    public Result<Void> create(@RequestBody ScenicSaveDTO dto) {
        scenicService.saveScenic(dto);
        return Result.ok();
    }

    /**
     * 编辑景点
     *
     * @param id  景点ID
     * @param dto 景点入参
     * @return 空结果
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ScenicSaveDTO dto) {
        scenicService.updateScenic(id, dto);
        return Result.ok();
    }

    /**
     * 删除景点（软删除）
     *
     * @param id 景点ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scenicService.removeScenic(id);
        return Result.ok();
    }
}
