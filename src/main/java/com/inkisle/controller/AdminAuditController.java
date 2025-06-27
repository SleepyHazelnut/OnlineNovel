package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.service.BookAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端的书籍审核。作者端只负责提交稿件，审核通过或驳回在此处决定；
 * 审核人 id 取自拦截器写入 request 的 `userId`，不接收前端传入
 * <p>
 * 除了审核，管理端还要能处理「已经上架之后才出问题」的书，
 * 故另有下架、恢复上架两个入口，以及供审核员查看正文的预览接口
 */
@RestController
@RequestMapping("/admin/audit")
public class AdminAuditController {

    @Autowired
    private BookAuditService bookAuditService;

    /**
     * 审核列表，默认只看待审的（status=0）
     */
    @GetMapping("/list")
    public Result<List<BookAuditRecord>> list(@RequestParam(defaultValue = "0") Integer status) {
        return Result.ok(bookAuditService.listByStatus(status));
    }

    /**
     * 审核通过
     */
    @PostMapping("/pass")
    public Result<Void> pass(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        bookAuditService.pass(params.get("id"), adminId);
        return Result.ok();
    }

    /**
     * 驳回
     * 请求体需同时携带 id（数字）与 remark（字符串），故用 Map 接收，
     * id 由 `Long.valueOf` 转为 Long
     */
    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        Long id = Long.valueOf(params.get("id").toString());
        bookAuditService.reject(id, (String) params.get("remark"), adminId);
        return Result.ok();
    }

    /**
     * 已上架 / 已下架的作品列表，供管理端决定下架或恢复
     * 审核记录列表只覆盖「送审过的那一次」，管不到上架之后的情况
     */
    @GetMapping("/books")
    public Result<Map<String, Object>> books(@RequestParam Integer auditStatus,
                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(bookAuditService.listBooks(auditStatus, pageNum, pageSize));
    }

    /**
     * 下架已上架的作品，必须写原因，作者能看到
     */
    @PostMapping("/take-down")
    public Result<Void> takeDown(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        Long bookId = Long.valueOf(params.get("bookId").toString());
        bookAuditService.takeDown(bookId, (String) params.get("remark"), adminId);
        return Result.ok();
    }

    /**
     * 恢复上架，用于处理下架错了的情况
     */
    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        bookAuditService.restore(params.get("bookId"), adminId);
        return Result.ok();
    }

    /**
     * 审核预览：作品信息与章节目录
     * 待审作品在读者端是看不到的，审核员若只能看书名，审核就没有依据
     */
    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam Long bookId) {
        return Result.ok(bookAuditService.preview(bookId));
    }

    /**
     * 预览某一章正文，章节必须属于该作品
     */
    @GetMapping("/chapter")
    public Result<String> chapter(@RequestParam Long bookId, @RequestParam Long chapterId) {
        return Result.ok(bookAuditService.chapterContent(bookId, chapterId));
    }

}
