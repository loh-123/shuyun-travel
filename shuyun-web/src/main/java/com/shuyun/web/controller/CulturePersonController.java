package com.shuyun.web.controller;

import com.shuyun.common.Result;
import com.shuyun.pojo.vo.CulturePersonDetailVO;
import com.shuyun.pojo.vo.CulturePersonVO;
import com.shuyun.service.CulturePersonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 传承人接口
 */
@RestController
@RequestMapping("/api/person")
@RequiredArgsConstructor
public class CulturePersonController {

    private final CulturePersonService culturePersonService;

    /**
     * 按文化ID查询传承人列表
     *
     * @param cultureId 文化ID
     * @return 传承人列表
     */
    @GetMapping("/list")
    public Result<List<CulturePersonVO>> list(@RequestParam Long cultureId) {
        return Result.ok(culturePersonService.listByCultureId(cultureId));
    }

    /**
     * 传承人详情
     *
     * @param id 传承人ID
     * @return 传承人详情
     */
    @GetMapping("/{id}")
    public Result<CulturePersonDetailVO> detail(@PathVariable Long id) {
        return Result.ok(culturePersonService.getDetail(id));
    }

    /**
     * 全部传承人（限量）
     *
     * @param limit 返回条数上限，默认 20
     * @return 传承人列表
     */
    @GetMapping("/all")
    public Result<List<CulturePersonVO>> all(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return Result.ok(culturePersonService.listAll(limit));
    }
}
