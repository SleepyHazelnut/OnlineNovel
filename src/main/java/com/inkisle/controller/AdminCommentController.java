package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.BookCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端的评论管理
 * 评论是读者发布的内容，管理端不修改正文，只做检索与删除，
 * 处置违规评论时保留原文没有意义
 */
@RestController
@RequestMapping("/admin/comment")
public class AdminCommentController {

    @Autowired
    private BookCommentService bookCommentService;

    /**
     * 评论列表，分页返回。keyword 同时匹配评论内容、书名、昵称
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(bookCommentService.pageAll(keyword, pageNum, pageSize));
    }

    /**
     * 删除评论，一级评论的回复一并删除
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookCommentService.adminDelete(id);
        return Result.ok();
    }

}
