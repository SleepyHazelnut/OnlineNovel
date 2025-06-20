package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.util.Md5Util;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传，仅接收图片，直接落到本机磁盘
 * 仅校验后缀名无法拦截改名伪装的文本文件，故额外增加一层文件头校验
 */
@RestController
@RequestMapping("/file")
public class FileController {

    /** 后缀名白名单，不在名单内直接拒绝 */
    private static final Set<String> ALLOWED_SUFFIX = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 按天分目录存放，避免单目录文件过多 */
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Value("${file.upload-path}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("上传文件为空");
        }

        String suffix = suffixOf(file.getOriginalFilename());
        if (!ALLOWED_SUFFIX.contains(suffix)) {
            return Result.fail("只支持 jpg / jpeg / png / gif / webp 格式的图片");
        }

        byte[] content = file.getBytes();
        if (!isRealImage(content)) {
            return Result.fail("文件内容不是图片");
        }

        // 以内容 MD5 作为文件名，相同图片只存一份
        String md5 = Md5Util.md5(content);

        String dateDir = LocalDate.now().format(DATE_DIR);
        Path dir = Paths.get(uploadPath, dateDir);
        Files.createDirectories(dir);

        Path target = dir.resolve(md5 + "." + suffix);
        boolean exists = Files.exists(target);
        if (!exists) {
            file.transferTo(target.toAbsolutePath().toFile());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("url", "/upload/" + dateDir + "/" + md5 + "." + suffix);
        // 内容相同的文件此前已上传，本次视为秒传
        data.put("repeat", exists);
        return Result.ok(data);
    }

    /**
     * 仅校验后缀名无法拦截：文本文件改名为 jpg 后仍可上传，
     * 故此处再校验文件头（magic number）
     */
    private boolean isRealImage(byte[] content) {
        if (content.length < 12) {
            return false;
        }
        // JPEG: FF D8
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8) {
            return true;
        }
        // PNG: 89 50 4E 47
        if ((content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
            return true;
        }
        // GIF: 47 49 46 38
        if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F' && content[3] == '8') {
            return true;
        }
        // WEBP: RIFF + 第 8 位起是 WEBP
        return content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }

    private String suffixOf(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
    }

}
