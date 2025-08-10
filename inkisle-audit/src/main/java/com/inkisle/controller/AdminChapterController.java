package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.RemarkDTO;
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
 * 管理端章节审核。作者保存时进待审，过了才出现在读者目录里
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

    @GetMapping("/{id}/content")
    public Result<Map<String, Object>> content(@PathVariable Long id) {
        return Result.ok(chapterAuditService.detail(id));
    }

    @PostMapping("/{id}/pass")
    public Result<Void> pass(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        chapterAuditService.pass(id, adminId);
        return Result.ok();
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody RemarkDTO dto,
                               HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        chapterAuditService.reject(id, dto.getRemark(), adminId);
        return Result.ok();
    }

}
