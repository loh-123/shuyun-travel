package com.shuyun.web.controller;

import com.shuyun.common.BusinessException;
import com.shuyun.common.Result;
import com.shuyun.pojo.vo.BackupFileVO;
import com.shuyun.pojo.vo.BackupTableVO;
import com.shuyun.service.AdminBackupService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据备份与恢复接口（后台，仅管理员）
 *
 * <p>备份文件保存到项目根目录 {@code /backup/}；权限校验见 {@code AdminInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/admin/backup")
@RequiredArgsConstructor
public class AdminBackupController {

    private final AdminBackupService adminBackupService;

    /**
     * 可备份的数据表及记录数
     *
     * @return 数据表列表
     */
    @GetMapping("/tables")
    public Result<List<BackupTableVO>> tables() {
        return Result.ok(adminBackupService.listTables());
    }

    /**
     * 创建备份
     *
     * @param body 请求体：{tables: [表名...]}
     * @return 生成的备份文件信息
     */
    @PostMapping("/create")
    public Result<BackupFileVO> create(@RequestBody Map<String, Object> body) {
        Object tables = body.get("tables");
        if (!(tables instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessException("请至少选择一张数据表");
        }
        List<String> names = list.stream().map(Object::toString).collect(Collectors.toList());
        return Result.ok(adminBackupService.createBackup(names));
    }

    /**
     * 备份历史列表
     *
     * @return 备份文件列表
     */
    @GetMapping("/list")
    public Result<List<BackupFileVO>> list() {
        return Result.ok(adminBackupService.listBackups());
    }

    /**
     * 下载备份文件
     *
     * @param filename 文件名
     * @param response 响应
     * @throws IOException 写出异常
     */
    @GetMapping("/download/{filename}")
    public void download(@PathVariable String filename, HttpServletResponse response) throws IOException {
        Path file = adminBackupService.resolveDownload(filename);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + filename);
        Files.copy(file, response.getOutputStream());
    }

    /**
     * 删除备份文件
     *
     * @param filename 文件名
     * @return 空结果
     */
    @DeleteMapping("/{filename}")
    public Result<Void> delete(@PathVariable String filename) {
        adminBackupService.deleteBackup(filename);
        return Result.ok();
    }

    /**
     * 恢复数据
     *
     * @param file   备份文件
     * @param tables 要恢复的表名（逗号分隔，可选；为空表示文件内全部表）
     * @return 实际插入的记录数
     * @throws IOException 读取异常
     */
    @PostMapping("/restore")
    public Result<Integer> restore(@RequestParam("file") MultipartFile file,
                                   @RequestParam(required = false) String tables) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传备份文件");
        }
        List<String> names = StringUtils.hasText(tables)
                ? Arrays.stream(tables.split(",")).map(String::trim).filter(StringUtils::hasText).collect(Collectors.toList())
                : List.of();
        int executed = adminBackupService.restore(file.getBytes(), names);
        return Result.ok(executed);
    }
}
