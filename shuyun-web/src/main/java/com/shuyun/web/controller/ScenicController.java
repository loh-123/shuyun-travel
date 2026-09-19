package com.shuyun.web.controller;

import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.City;
import com.shuyun.pojo.vo.ScenicDetailVO;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.ScenicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 景点 / 市州接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScenicController {

    private final ScenicService scenicService;

    /**
     * 景点分页列表
     *
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @param cityId  市州ID（可选）
     * @param type    景点类型（可选）
     * @param keyword 关键词（可选）
     * @return 分页结果
     */
    @GetMapping("/scenic/list")
    public Result<PageResult<ScenicSpotVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword) {
        return Result.ok(scenicService.pageScenic(page, size, cityId, type, keyword));
    }

    /**
     * 热门景点：评分最高的 6 个
     *
     * @return 景点列表
     */
    @GetMapping("/scenic/hot")
    public Result<List<ScenicSpotVO>> hot() {
        return Result.ok(scenicService.listHot());
    }

    /**
     * 景点详情
     *
     * @param id 景点ID
     * @return 景点详情 + 图片列表 + 同城推荐
     */
    @GetMapping("/scenic/{id}")
    public Result<ScenicDetailVO> detail(@PathVariable Long id) {
        return Result.ok(scenicService.getDetail(id));
    }

    /**
     * 全部市州
     *
     * @return 市州列表
     */
    @GetMapping("/city/list")
    public Result<List<City>> cityList() {
        return Result.ok(scenicService.listCities());
    }
}
