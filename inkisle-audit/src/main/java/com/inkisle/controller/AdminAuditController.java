package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.AuditHandleDTO;
import com.inkisle.dto.BookActionDTO;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.service.BookAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "0") Integer status,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(bookAuditService.pageByStatus(status, pageNum, pageSize));
    }

    @PostMapping("/pass")
    public Result<Void> pass(@Valid @RequestBody AuditHandleDTO dto, HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        bookAuditService.pass(dto.getId(), adminId);
        return Result.ok();
    }

    @PostMapping("/reject")
    public Result<Void> reject(@Valid @RequestBody AuditHandleDTO dto, HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        bookAuditService.reject(dto.getId(), dto.getRemark(), adminId);
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
    public Result<Void> takeDown(@Valid @RequestBody BookActionDTO dto, HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        bookAuditService.takeDown(dto.getBookId(), dto.getRemark(), adminId);
        return Result.ok();
    }

    @PostMapping("/restore")
    public Result<Void> restore(@Valid @RequestBody BookActionDTO dto, HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        bookAuditService.restore(dto.getBookId(), adminId);
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
