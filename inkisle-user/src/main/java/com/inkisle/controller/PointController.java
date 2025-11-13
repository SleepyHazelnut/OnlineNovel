package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.service.PointService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 读者端积分接口，只能看和花自己的分
 * userId 一律取登录态，请求体里传的一概不认
 */
@RestController
@RequestMapping("/point")
public class PointController {

    @Autowired
    private PointService pointService;

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(HttpServletRequest request) {
        return Result.ok(pointService.summary(CurrentUser.id(request)));
    }

    @GetMapping("/ledger")
    public Result<Map<String, Object>> ledger(@RequestParam(required = false) Integer pageNum,
                                              @RequestParam(required = false) Integer pageSize,
                                              HttpServletRequest request) {
        return Result.ok(pointService.ledger(CurrentUser.id(request), pageNum, pageSize));
    }

    /** 规则表是公开的：不登录也该知道怎么攒分，否则连注册的动力都没有 */
    @GetMapping("/rules")
    public Result<List<Map<String, Object>>> rules() {
        return Result.ok(pointService.rules());
    }

    /** 花 20 分给一本书投票，一人一本一票 */
    @PostMapping("/recommend/{bookId}")
    public Result<Map<String, Object>> recommend(@PathVariable Long bookId, HttpServletRequest request) {
        return Result.ok(pointService.recommend(CurrentUser.id(request), bookId));
    }

}
