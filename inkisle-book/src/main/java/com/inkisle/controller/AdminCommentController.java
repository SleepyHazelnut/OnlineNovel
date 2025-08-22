package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.RemarkDTO;
import com.inkisle.service.BookCommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端的评论管理
 * 读者发的评论先进待审，过了才给别人看。已经过审的后面发现有问题，也能直接删
 */
@RestController
@RequestMapping("/admin/comment")
public class AdminCommentController {

    @Autowired
    private BookCommentService bookCommentService;

    /**
     * 评论列表，分页
     * keyword 会同时匹配评论内容、书名和昵称，auditStatus 不传则不筛选状态
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer auditStatus,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(bookCommentService.pageAll(keyword, auditStatus, pageNum, pageSize));
    }

    @PostMapping("/{id}/pass")
    public Result<Void> pass(@PathVariable Long id, HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        bookCommentService.pass(id, adminId);
        return Result.ok();
    }

    /** 驳回，必须写原因 */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                               @RequestBody RemarkDTO dto,
                               HttpServletRequest request) {
        Long adminId = CurrentUser.id(request);
        bookCommentService.reject(id, dto.getRemark(), adminId);
        return Result.ok();
    }

    /** 删除，一级评论的回复一并删除 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookCommentService.adminDelete(id);
        return Result.ok();
    }

}
