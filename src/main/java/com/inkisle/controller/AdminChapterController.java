package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.ChapterAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端章节审核
 * 章节在作者保存时进入待审，通过后才出现在读者端目录里
 */
@RestController
@RequestMapping("/admin/chapter")
public class AdminChapterController {

    @Autowired
    private ChapterAuditService chapterAuditService;

    /**
     * 按审核状态分页列出章节，默认待审
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "0") Integer auditStatus,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(chapterAuditService.pageByStatus(auditStatus, pageNum, pageSize));
    }

    /**
     * 审核预览：章节信息与正文
     */
    @GetMapping("/{id}/content")
    public Result<Map<String, Object>> content(@PathVariable Long id) {
        return Result.ok(chapterAuditService.detail(id));
    }

    @PostMapping("/{id}/pass")
    public Result<Void> pass(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        chapterAuditService.pass(id, adminId);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody Map<String, Object> params,
                               HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        chapterAuditService.reject(id, (String) params.get("remark"), adminId);
        return Result.ok();
    }

}
