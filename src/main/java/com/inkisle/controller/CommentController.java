package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookComment;
import com.inkisle.service.BookCommentService;
import com.inkisle.service.BookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private BookCommentService bookCommentService;

    @Autowired
    private BookService bookService;

    /**
     * 评论列表，游客也能看
     */
    @GetMapping("/list")
    public Result<List<BookComment>> list(@RequestParam Long bookId) {
        // 与书籍详情同一口径：作品未过审时评论一并隐藏
        if (!bookService.isVisibleToReader(bookId)) {
            return Result.fail("作品不存在或未上架");
        }
        return Result.ok(bookCommentService.listByBookId(bookId));
    }

    /**
     * 发表评论或回复，要登录
     */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody BookComment comment, HttpServletRequest request) {
        if (!bookService.isVisibleToReader(comment.getBookId())) {
            return Result.fail("作品不存在或未上架");
        }
        Long userId = (Long) request.getAttribute("userId");
        // 以登录态的 userId 覆盖请求体中携带的值，不接收前端传入
        comment.setUserId(userId);
        bookCommentService.add(comment);
        return Result.ok();
    }

}
