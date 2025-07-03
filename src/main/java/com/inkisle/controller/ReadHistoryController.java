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

/** 阅读记录，userId 一律取登录态，请求体里传的丢掉 */
@RestController
@RequestMapping("/history")
public class ReadHistoryController {

    @Autowired
    private UserReadHistoryService userReadHistoryService;

    @Autowired
    private BookService bookService;

    @PostMapping("/save")
    public Result<Void> save(@RequestBody UserReadHistory history, HttpServletRequest request) {
        // 保存记录会顺带给榜单加热度，不拦的话可以拿未过审的书反复刷榜
        if (!bookService.isVisibleToReader(history.getBookId())) {
            return Result.fail("作品不存在或未上架");
        }
        history.setUserId((Long) request.getAttribute("userId"));
        userReadHistoryService.save(history);
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<List<UserReadHistory>> list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userReadHistoryService.listByUserId(userId));
    }

    @GetMapping("/last")
    public Result<UserReadHistory> last(@RequestParam Long bookId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userReadHistoryService.lastRead(userId, bookId));
    }

}
