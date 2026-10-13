package com.shuyun.web.controller;

import com.shuyun.common.BusinessException;
import com.shuyun.common.PageResult;
import com.shuyun.common.Result;
import com.shuyun.pojo.entity.User;
import com.shuyun.service.UserService;
import com.shuyun.web.util.SimpleExcelWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理接口（后台，仅管理员）
 *
 * <p>权限校验见 {@code AdminInterceptor}（拦截 /api/admin/** 并校验 role=ADMIN），
 * 等价于其他框架中的 {@code @PreAuthorize("hasRole('ADMIN')")}。</p>
 */
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    /** 时间格式化 */
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserService userService;

    /**
     * 用户分页列表
     *
     * @param keyword 关键词（用户名 / 邮箱，可选）
     * @param status  状态：0-禁用，1-正常；不传为全部
     * @param role    角色：USER / ADMIN；不传为全部
     * @param page    页码，默认 1
     * @param size    每页大小，默认 10
     * @return 分页结果
     */
    @GetMapping("/list")
    public Result<PageResult<User>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String role,
                                         @RequestParam(required = false) String startDate,
                                         @RequestParam(required = false) String endDate,
                                         @RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(userService.pageUsers(page, size, keyword, status, role, startDate, endDate));
    }

    /**
     * 用户详情（含最近行为记录）
     *
     * @param id 用户ID
     * @return {user, behaviors}
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user", user);
        data.put("behaviors", userService.recentBehaviors(id, 10));
        return Result.ok(data);
    }

    /**
     * 修改用户状态（启用 / 禁用）
     *
     * @param id   用户ID
     * @param body 请求体：{status}
     * @return 空结果
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object status = body.get("status");
        if (status == null) {
            throw new BusinessException("状态不能为空");
        }
        userService.updateStatus(id, Integer.valueOf(status.toString()));
        return Result.ok();
    }

    /**
     * 重置用户密码为默认密码（123456）
     *
     * @param id 用户ID
     * @return 重置后的明文密码
     */
    @PutMapping("/{id}/reset-password")
    public Result<String> resetPassword(@PathVariable Long id) {
        return Result.ok(userService.resetPassword(id));
    }

    /**
     * 删除用户（软删除）
     *
     * @param id 用户ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userService.removeUser(id);
        return Result.ok();
    }

    /**
     * 导出用户列表为 Excel（.xlsx）
     *
     * @param keyword   关键词（可选）
     * @param status    状态（可选）
     * @param role      角色（可选）
     * @param response  响应
     * @throws IOException 写出异常
     */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) String role,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate,
                       HttpServletResponse response) throws IOException {
        List<User> users = userService.listUsersForExport(keyword, status, role, startDate, endDate);

        String[] headers = {"ID", "用户名", "邮箱", "角色", "状态", "注册时间", "最后登录时间"};
        List<String[]> rows = new ArrayList<>();
        for (User user : users) {
            rows.add(new String[]{
                    String.valueOf(user.getId()),
                    user.getUsername(),
                    user.getEmail(),
                    user.getRole(),
                    Integer.valueOf(1).equals(user.getStatus()) ? "正常" : "禁用",
                    format(user.getCreateTime()),
                    format(user.getLastLoginTime())
            });
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=users.xlsx");
        SimpleExcelWriter.write(response.getOutputStream(), "用户列表", headers, rows);
    }

    /** 时间格式化，空值返回空串 */
    private String format(LocalDateTime time) {
        return time == null ? "" : DATE_TIME.format(time);
    }
}
