package com.shuyun.service;

import java.util.Map;

/**
 * 聚合搜索业务接口
 *
 * <p>一次搜索同时返回「市州 / 景点 / 文化」三类结果，供顶部搜索浮层使用。</p>
 */
public interface SearchService {

    /**
     * 聚合搜索
     *
     * @param keyword 关键词（为空时只返回热门搜索词）
     * @param limit   每类最多返回条数
     * @return {keyword, hotKeywords, cities, scenics, cultures}
     */
    Map<String, Object> search(String keyword, int limit);
}
