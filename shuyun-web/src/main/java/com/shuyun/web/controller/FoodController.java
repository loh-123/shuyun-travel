package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.vo.FoodDetailVO;
import com.shuyun.pojo.vo.FoodVO;
import com.shuyun.service.FoodService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 美食接口
 */
@RestController
@RequestMapping("/api/food")
@RequiredArgsConstructor
public class FoodController {

    private final FoodService foodService;

    /**
     * 美食分页列表
     *
     * @param page     页码，默认 1
     * @param size     每页大小，默认 12
     * @param category 分类（可选）
     * @param cityName 所属城市（可选）
     * @param keyword  关键词（可选）
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<FoodVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) String keyword) {
        return Result.ok(foodService.listFood(page, size, category, cityName, keyword));
    }

    /**
     * 美食分类列表（动态去重）
     *
     * @return 分类列表
     */
    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.ok(foodService.listCategories());
    }

    /**
     * 美食详情
     *
     * @param id 美食ID
     * @return 美食详情
     */
    @GetMapping("/{id}")
    public Result<FoodDetailVO> detail(@PathVariable Long id) {
        return Result.ok(foodService.getFoodDetail(id));
    }
}
