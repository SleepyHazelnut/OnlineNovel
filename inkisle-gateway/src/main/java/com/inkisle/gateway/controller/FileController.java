package com.inkisle.gateway.controller;

import com.inkisle.common.Result;
import com.inkisle.util.Md5Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
 * 图片上传，落到本机磁盘
 * 放在网关是因为 /upload 的读取本就在网关，读写共用同一目录，
 * 放在同一进程内少一次转发，挂载目录也只需挂一处
 * 网关是 WebFlux，参数用 FilePart 不是 MultipartFile，整条链走 Mono 不 block
 */
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    /** 后缀名白名单，不在名单内直接拒绝 */
    private static final Set<String> ALLOWED_SUFFIX = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /**
     * 单张图上限
     *
     * max-in-memory-size 只决定「超过就落临时文件」，不是拒绝；下面的 join 仍会把整份内容
     * 读回一个 byte[]，所以一个几百兆的「图片」足够把网关堆打满。入口先卡住长度
     */
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    /** 按天分目录存放，避免单目录文件过多 */
    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Value("${file.upload-path}")
    private String uploadPath;

    @PostMapping("/upload")
    public Mono<Result<Map<String, Object>>> upload(@RequestPart(name = "file", required = false) FilePart file) {
        if (file == null) {
            return Mono.just(Result.fail("上传文件为空"));
        }

        String suffix = suffixOf(file.filename());
        if (!ALLOWED_SUFFIX.contains(suffix)) {
            return Mono.just(Result.fail("只支持 jpg / jpeg / png / gif / webp 格式的图片"));
        }
        // 声明长度只是个提示，客户端可以撒谎，所以读完之后还会再按实际字节数判一次
        if (file.headers().getContentLength() > MAX_BYTES) {
            return Mono.just(Result.fail("图片不能超过 5MB"));
        }

        // 内容要读全才能算 MD5 与校验文件头，读完之后再切到弹性线程池写盘，
        // 磁盘 IO 是阻塞的，不能留在 Netty 的事件循环上
        return DataBufferUtils.join(file.content())
                .map(FileController::toBytes)
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    if (bytes.length > MAX_BYTES) {
                        return Mono.just(Result.<Map<String, Object>>fail("图片不能超过 5MB"));
                    }
                    return Mono.fromCallable(() -> store(bytes, suffix))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    /**
     * 落盘。文件名取内容 MD5，同一张图重复上传只存一份，返回的 repeat 供前端提示秒传
     */
    private Result<Map<String, Object>> store(byte[] content, String suffix) {
        if (content.length == 0) {
            return Result.fail("上传文件为空");
        }
        // 仅校验后缀不足，把 txt 改成 jpg 仍能上传，因此再校验文件头
        if (!isRealImage(content)) {
            return Result.fail("文件内容不是图片");
        }

        String md5 = Md5Util.md5(content);
        String dateDir = LocalDate.now().format(DATE_DIR);
        try {
            Path dir = Paths.get(uploadPath, dateDir);
            Files.createDirectories(dir);

            Path target = dir.resolve(md5 + "." + suffix);
            boolean exists = Files.exists(target);
            if (!exists) {
                Files.write(target, content);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("url", "/upload/" + dateDir + "/" + md5 + "." + suffix);
            data.put("repeat", exists);
            return Result.ok(data);
        } catch (IOException e) {
            // 异常信息里带的是服务端绝对路径，不该回给前端
            log.error("上传落盘失败 md5={}", md5, e);
            return Result.fail("文件保存失败，请稍后重试");
        }
    }

    private static byte[] toBytes(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            // 池化的 DataBuffer 必须显式释放，否则内存会逐渐耗尽
            DataBufferUtils.release(buffer);
        }
    }

    /**
     * 校验文件头（magic number）：JPEG、PNG、GIF、WEBP 四种
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
