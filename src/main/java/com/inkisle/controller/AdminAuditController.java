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

/** 管理端审核。审核人 id 取自拦截器写入的 userId，不接收前端传的 */
@RestController
@RequestMapping("/admin/audit")
public class AdminAuditController {

    @Autowired
    private BookAuditService bookAuditService;

    @GetMapping("/list")
    public Result<List<BookAuditRecord>> list(@RequestParam(defaultValue = "0") Integer status) {
        return Result.ok(bookAuditService.listByStatus(status));
    }

    @PostMapping("/pass")
    public Result<Void> pass(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        bookAuditService.pass(params.get("id"), adminId);
        return Result.ok();
    }

    @PostMapping("/reject")
    public Result<Void> reject(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        Long id = Long.valueOf(params.get("id").toString());
        bookAuditService.reject(id, (String) params.get("remark"), adminId);
        return Result.ok();
    }

    /** 审核记录管不到上架之后，这里按上架状态查，供下架、恢复用 */
    @GetMapping("/books")
    public Result<Map<String, Object>> books(@RequestParam Integer auditStatus,
                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(bookAuditService.listBooks(auditStatus, pageNum, pageSize));
    }

    @PostMapping("/take-down")
    public Result<Void> takeDown(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        Long bookId = Long.valueOf(params.get("bookId").toString());
        bookAuditService.takeDown(bookId, (String) params.get("remark"), adminId);
        return Result.ok();
    }

    @PostMapping("/restore")
    public Result<Void> restore(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        bookAuditService.restore(params.get("bookId"), adminId);
        return Result.ok();
    }

    /** 待审作品读者端看不到，审核员得能看作品信息与目录 */
    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam Long bookId) {
        return Result.ok(bookAuditService.preview(bookId));
    }

    @GetMapping("/chapter")
    public Result<String> chapter(@RequestParam Long bookId, @RequestParam Long chapterId) {
        return Result.ok(bookAuditService.chapterContent(bookId, chapterId));
    }

}
