package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.UserReadHistory;
import com.inkisle.service.UserReadHistoryService;
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

/**
 * 阅读记录
 * userId 一律取自拦截器解析 token 后写入 request 的属性，请求体中携带的也一并丢弃
 * 不可由前端传参：否则修改参数即可查看他人记录
 */
@RestController
@RequestMapping("/history")
public class ReadHistoryController {

    @Autowired
    private UserReadHistoryService userReadHistoryService;

    @Autowired
    private BookService bookService;

    /**
     * 保存阅读位置，前端翻页的时候调
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody UserReadHistory history, HttpServletRequest request) {
        // 保存阅读记录会同时为榜单增加热度，故此处也需拦截未过审的作品；
        // 否则可反复以其 id 调用，将未过审作品刷上热度榜
        if (!bookService.isVisibleToReader(history.getBookId())) {
            return Result.fail("作品不存在或未上架");
        }
        // 请求体中即使携带 userId 也直接丢弃，只采用当前登录用户
        history.setUserId((Long) request.getAttribute("userId"));
        userReadHistoryService.save(history);
        return Result.ok();
    }

    /**
     * 当前用户的阅读历史，userId 同样只取登录态中的值
     */
    @GetMapping("/list")
    public Result<List<UserReadHistory>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userReadHistoryService.listByUserId(userId));
    }

    /**
     * 某本书上次读到哪，续读的时候用
     */
    @GetMapping("/last")
    public Result<UserReadHistory> last(@RequestParam Long bookId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userReadHistoryService.lastRead(userId, bookId));
    }

}
