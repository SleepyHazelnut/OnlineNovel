package com.inkisle.controller;

import com.inkisle.common.Result;
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
import java.util.UUID;

/**
 * 文件上传，目前只支持图片，直接落本机磁盘
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${file.upload-path}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("上传文件为空");
        }

        String originalName = file.getOriginalFilename();
        String suffix = "";
        if (originalName != null && originalName.contains(".")) {
            suffix = originalName.substring(originalName.lastIndexOf("."));
        }

        // 用 UUID 重命名，避免同名文件互相覆盖，也顺手挡掉了原文件名里的路径字符
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;

        Path dir = Paths.get(uploadPath);
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(fileName).toAbsolutePath().toFile());

        return Result.ok("/upload/" + fileName);
    }

}
