package com.shuyun.web.controller;

import com.shuyun.common.BusinessException;
import com.shuyun.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 后台通用图片上传接口（仅管理员）
 *
 * <p>保存到 {@code shuyun.upload-dir} 配置的目录下（默认 ./upload，可通过 /upload/** 静态访问），
 * 返回可直接用于 img src 的相对地址，例如 {@code /upload/admin/xxxx.png}。</p>
 */
@RestController
@RequestMapping("/api/admin/upload")
@RequiredArgsConstructor
public class AdminUploadController {

    /** 允许上传的图片扩展名 */
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 上传文件保存目录（与本项目其它上传一致，通过 /upload/** 对外访问） */
    @Value("${shuyun.upload-dir:./upload}")
    private String uploadDir;

    /** 外部访问基础地址：前后端分域部署时拼接上传文件绝对地址（本地留空返回相对路径） */
    @Value("${shuyun.base-url:}")
    private String baseUrl;

    /**
     * 上传图片
     *
     * @param file 图片文件
     * @return 图片可访问地址（/upload/admin/xxx.ext）
     * @throws IOException 写入异常
     */
    @PostMapping("/image")
    public Result<String> image(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择图片文件");
        }

        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase()
                : "";
        if (!IMAGE_EXT.contains(ext)) {
            throw new BusinessException("仅支持 jpg / png / webp / gif 格式的图片");
        }

        Path dir = Paths.get(uploadDir, "admin").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        file.transferTo(dir.resolve(fileName).toFile());

        return Result.ok(baseUrl + "/upload/admin/" + fileName);
    }
}
