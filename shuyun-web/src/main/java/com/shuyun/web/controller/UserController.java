package com.shuyun.web.controller;

import com.shuyun.common.BusinessException;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.User;
import com.shuyun.pojo.vo.ScenicSpotVO;
import com.shuyun.service.AiChatService;
import com.shuyun.service.CollectionService;
import com.shuyun.service.HistoryService;
import com.shuyun.service.TripService;
import com.shuyun.service.UserService;
import com.shuyun.web.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 用户中心接口
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    /** 允许上传的头像扩展名 */
    private static final Set<String> AVATAR_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final UserService userService;

    private final CollectionService collectionService;

    private final TripService tripService;

    private final HistoryService historyService;

    private final AiChatService aiChatService;

    /** 上传文件保存目录 */
    @Value("${shuyun.upload-dir:./upload}")
    private String uploadDir;

    /** 外部访问基础地址：前后端分域部署时拼接上传文件绝对地址（本地留空返回相对路径） */
    @Value("${shuyun.base-url:}")
    private String baseUrl;

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
     * 修改资料（用户名 / 邮箱 / 一句话简介）
     *
     * @param body 请求体：{username, email, bio}，均可选
     * @return 修改后的用户（不含密码）
     */
    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestBody Map<String, String> body) {
        User user = userService.updateProfile(
                UserContext.getUserId(),
                body.get("username"),
                body.get("email"),
                body.get("bio"));
        return Result.ok(user);
    }

    /**
     * 上传头像：保存到 {upload-dir}/avatar/，并同步更新用户头像
     *
     * @param file 图片文件
     * @return 头像可访问地址（/upload/avatar/xxx.png）
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片文件");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!AVATAR_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg / png / webp / gif 格式的图片");
        }

        Path dir = Paths.get(uploadDir, "avatar").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        file.transferTo(dir.resolve(fileName).toFile());

        String url = baseUrl + "/upload/avatar/" + fileName;
        userService.updateAvatar(UserContext.getUserId(), url);
        return Result.ok(url);
    }

    /**
     * 数据概览：去过的城市 / 收藏景点 / 生成行程 / AI 对话
     *
     * @return {cities, collections, trips, chats}
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long userId = UserContext.getUserId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cities", historyService.countCities(userId));
        data.put("collections", collectionService.listScenicCollections(userId).size());
        data.put("trips", tripService.listMyTrips(userId).size());
        data.put("chats", aiChatService.listSessions(userId).size());
        return Result.ok(data);
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
