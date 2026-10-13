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
     * @param size    每页大小，默认 12
     * @param cityId  市州ID（可选）
     * @param type    景点类型（可选）
     * @param keyword 关键词（可选）
     * @param sort    排序：default/综合、score/评分、hot/人气（可选，兼容旧参数）
     * @return 分页结果
     */
    @GetMapping("/scenic/list")
    public Result<PageResult<ScenicSpotVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "12") Integer size,
            @RequestParam(required = false) Long cityId,
            @RequestParam(required = false) String cityName,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "default") String sort) {
        // cityName 兼容：按名称（含「成都市」这类全称）换算成 cityId
        Long resolvedCityId = cityId;
        if (resolvedCityId == null && org.springframework.util.StringUtils.hasText(cityName)) {
            String shortName = cityName
                    .replaceAll("(藏族羌族自治州|藏族自治州|彝族自治州|回族自治州|苗族自治州|自治州|自治县|地区|市|州)$", "")
                    .trim();
            resolvedCityId = scenicService.listCities().stream()
                    .filter(c -> shortName.equals(c.getName()) || cityName.startsWith(c.getName()))
                    .map(City::getId)
                    .findFirst()
                    .orElse(null);
        }
        return Result.ok(scenicService.pageScenic(page, size, resolvedCityId, type, keyword, sort));
    }

    /**
     * 热门景点：按评分倒序
     *
     * @param limit 数量，默认 6
     * @return 景点列表
     */
    @GetMapping("/scenic/hot")
    public Result<List<ScenicSpotVO>> hot(@RequestParam(defaultValue = "6") Integer limit) {
        return Result.ok(scenicService.getHotScenic(limit));
    }

    /**
     * 城市列表（动态：景点表中出现过的市州名称）
     *
     * @return 市州名称列表
     */
    @GetMapping("/scenic/cities")
    public Result<List<String>> cities() {
        return Result.ok(scenicService.listCityNames());
    }

    /**
     * 景点详情
     *
     * @param id 景点ID
     * @return 景点详情 + 图片列表 + 同城推荐
     */
    @GetMapping("/scenic/{id}")
    public Result<ScenicDetailVO> detail(@PathVariable Long id) {
        return Result.ok(scenicService.getScenicDetail(id));
    }

    /**
     * 全部市州（city 表全量，供筛选下拉使用）
     *
     * @return 市州列表
     */
    @GetMapping("/city/list")
    public Result<List<City>> cityList() {
        return Result.ok(scenicService.listCities());
    }
}
