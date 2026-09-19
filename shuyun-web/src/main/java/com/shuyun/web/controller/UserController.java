package com.shuyun.web.controller;

import com.shuyun.common.BusinessException;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.User;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.CollectionService;
import com.shuyun.service.UserService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户中心接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final CollectionService collectionService;

    /**
     * 当前登录用户信息
     *
     * @return 用户信息（不含密码）
     */
    @GetMapping("/profile")
    public Result<User> profile() {
        User user = userService.getCurrentUser(UserContext.getUserId());
        user.setPassword(null);
        return Result.ok(user);
    }

    /**
     * 修改密码
     *
     * @param body 请求体：{oldPassword, newPassword}
     * @return 统一结果
     */
    @PutMapping("/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            throw new BusinessException("原密码和新密码不能为空");
        }
        userService.changePassword(UserContext.getUserId(), oldPassword, newPassword);
        return Result.ok();
    }

    /**
     * 我的收藏（景点）
     *
     * @return 收藏的景点列表
     */
    @GetMapping("/collection")
    public Result<List<ScenicSpotVO>> collection() {
        return Result.ok(collectionService.listScenicCollections(UserContext.getUserId()));
    }

    /**
     * 收藏景点
     *
     * @param body 请求体：{targetType, targetId}
     * @return 统一结果
     */
    @PostMapping("/collection")
    public Result<Void> collect(@RequestBody Map<String, Object> body) {
        Object targetId = body.get("targetId");
        if (targetId == null) {
            throw new BusinessException("收藏对象不能为空");
        }
        Object targetType = body.get("targetType");
        collectionService.collect(
                UserContext.getUserId(),
                targetType == null ? null : targetType.toString(),
                Long.valueOf(targetId.toString()));
        return Result.ok();
    }

    /**
     * 取消收藏
     *
     * @param targetType 收藏对象类型，默认 SCENIC
     * @param targetId   收藏对象ID
     * @return 统一结果
     */
    @DeleteMapping("/collection")
    public Result<Void> removeCollection(@RequestParam(required = false) String targetType,
                                         @RequestParam Long targetId) {
        collectionService.remove(UserContext.getUserId(), targetType, targetId);
        return Result.ok();
    }

    /**
     * 是否已收藏
     *
     * @param targetType 收藏对象类型，默认 SCENIC
     * @param targetId   收藏对象ID
     * @return true-已收藏
     */
    @GetMapping("/collection/check")
    public Result<Boolean> checkCollection(@RequestParam(required = false) String targetType,
                                           @RequestParam Long targetId) {
        return Result.ok(collectionService.isCollected(UserContext.getUserId(), targetType, targetId));
    }
}
