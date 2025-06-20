package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.SignService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 签到。以下接口均需登录，userId 取自拦截器写入 request 的属性
 */
@RestController
@RequestMapping("/sign")
public class SignController {

    @Autowired
    private SignService signService;

    /**
     * 打卡，同时把「今天是不是第一次签」告诉前端
     */
    @PostMapping("/in")
    public Result<Map<String, Object>> signIn(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        boolean first = signService.sign(userId);

        Map<String, Object> data = new HashMap<>();
        // 重复签到不视为失败，返回 repeat 标记由前端自行提示
        data.put("repeat", !first);
        data.put("continuousDays", signService.continuousDays(userId));
        return Result.ok(data);
    }

    /**
     * 本月签到情况，页面画日历用
     */
    @GetMapping("/month")
    public Result<Map<String, Object>> month(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(signService.monthStatus(userId));
    }

    /**
     * 连续签到天数
     */
    @GetMapping("/continuous")
    public Result<Integer> continuous(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(signService.continuousDays(userId));
    }

}
