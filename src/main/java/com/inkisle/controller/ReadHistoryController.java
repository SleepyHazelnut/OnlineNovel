package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.UserReadHistory;
import com.inkisle.service.UserReadHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/history")
public class ReadHistoryController {

    @Autowired
    private UserReadHistoryService userReadHistoryService;

    /**
     * 保存阅读位置，翻页时前端调
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody UserReadHistory history) {
        userReadHistoryService.save(history);
        return Result.ok();
    }

    /**
     * 我的阅读历史
     */
    @GetMapping("/list")
    public Result<List<UserReadHistory>> list(@RequestParam Long userId) {
        List<UserReadHistory> list = userReadHistoryService.listByUserId(userId);
        return Result.ok(list);
    }

    /**
     * 某本书上次读到哪
     */
    @GetMapping("/last")
    public Result<UserReadHistory> last(@RequestParam Long userId, @RequestParam Long bookId) {
        return Result.ok(userReadHistoryService.lastRead(userId, bookId));
    }

}
