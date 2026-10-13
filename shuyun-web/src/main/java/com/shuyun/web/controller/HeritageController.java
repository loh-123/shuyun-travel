package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.vo.HeritageDetailVO;
import com.shuyun.pojo.vo.HeritageVO;
import com.shuyun.service.HeritageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 非物质文化遗产接口
 */
@RestController
@RequestMapping("/api/heritage")
@RequiredArgsConstructor
public class HeritageController {

    private final HeritageService heritageService;

    /**
     * 非遗分页列表
     *
     * @param page     页码，默认 1
     * @param size     每页大小，默认 12
     * @param category 类别（可选）
     * @param level    级别（可选）
     * @param keyword  关键词（可选）
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<HeritageVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String keyword) {
        return Result.ok(heritageService.listHeritage(page, size, category, level, keyword));
    }

    /**
     * 非遗详情（含图片集）
     *
     * @param id 非遗ID
     * @return 非遗详情
     */
    @GetMapping("/{id}")
    public Result<HeritageDetailVO> detail(@PathVariable Long id) {
        return Result.ok(heritageService.getHeritageDetail(id));
    }
}
