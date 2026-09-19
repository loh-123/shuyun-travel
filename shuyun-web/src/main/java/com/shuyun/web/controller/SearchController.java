package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 聚合搜索接口（公开，无需登录）
 */
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * 聚合搜索：市州 + 景点 + 文化
     *
     * @param keyword 关键词，为空时仅返回热门搜索词
     * @param limit   每类最多返回条数，默认 6
     * @return {keyword, hotKeywords, cities, scenics, cultures}
     */
    @GetMapping
    public Result<Map<String, Object>> search(@RequestParam(required = false) String keyword,
                                              @RequestParam(defaultValue = "6") Integer limit) {
        return Result.ok(searchService.search(keyword, limit));
    }
}
