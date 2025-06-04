package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookComment;
import com.inkisle.service.BookCommentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private BookCommentService bookCommentService;

    /**
     * 评论列表，游客也能看
     */
    @GetMapping("/list")
    public Result list(@RequestParam Long bookId) {
        return Result.ok(bookCommentService.listByBookId(bookId));
    }

    /**
     * 发表评论或回复，需要登录
     */
    @PostMapping("/add")
    public Result add(@RequestBody BookComment comment, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        comment.setUserId(userId);
        bookCommentService.add(comment);
        return Result.ok();
    }

}
