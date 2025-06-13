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

}
