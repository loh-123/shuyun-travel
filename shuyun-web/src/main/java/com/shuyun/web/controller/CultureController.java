package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.pojo.vo.CultureDetailVO;
import com.shuyun.pojo.vo.CultureVO;
import com.shuyun.service.CultureService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文化接口
 */
@RestController
@RequestMapping("/api/culture")
@RequiredArgsConstructor
public class CultureController {

    private final CultureService cultureService;

    /**
     * 全部文化条目（不分页）
     *
     * @return 文化列表
     */
    @GetMapping("/list")
    public Result<List<CultureVO>> list() {
        return Result.ok(cultureService.listCulture());
    }

    /**
     * 按分类查询
     *
     * @param category 分类
     * @return 文化列表
     */
    @GetMapping("/category/{category}")
    public Result<List<CultureVO>> byCategory(@PathVariable String category) {
        return Result.ok(cultureService.listByCategory(category));
    }

    /**
     * 文化详情
     *
     * @param id 文化ID
     * @return 文化详情（含图片集 / 关联景点 / 传承人）
     */
    @GetMapping("/{id}")
    public Result<CultureDetailVO> detail(@PathVariable Long id) {
        return Result.ok(cultureService.getCultureDetail(id));
    }
}
