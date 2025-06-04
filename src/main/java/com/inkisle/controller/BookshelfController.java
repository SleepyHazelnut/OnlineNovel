package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.UserBookshelfService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/bookshelf")
public class BookshelfController {

    @Autowired
    private UserBookshelfService userBookshelfService;

    /**
     * 加入书架
     */
    @PostMapping("/add")
    public Result add(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userBookshelfService.add(userId, params.get("bookId"));
        return Result.ok();
    }

    /**
     * 移出书架
     */
    @PostMapping("/remove")
    public Result remove(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userBookshelfService.remove(userId, params.get("bookId"));
        return Result.ok();
    }

    /**
     * 我的书架
     */
    @GetMapping("/list")
    public Result list(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userBookshelfService.listByUserId(userId));
    }

    /**
     * 这本书我收藏了没有，详情页要显示不同的按钮
     */
    @GetMapping("/check")
    public Result check(@RequestParam Long bookId, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(userBookshelfService.isCollected(userId, bookId));
    }

}
