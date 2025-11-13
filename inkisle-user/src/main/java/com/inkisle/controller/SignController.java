package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.SignResult;
import com.inkisle.service.PointService;
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

    @Autowired
    private PointService pointService;

    /**
     * 打卡。返回本次拿到的分和最新余额，前端据此提示「+2 分，当前 44 分」，
     * 不必再单独请求一次积分接口
     */
    @PostMapping("/in")
    public Result<Map<String, Object>> signIn(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        SignResult result = signService.sign(userId);

        Map<String, Object> data = new HashMap<>();
        // 重复签到不视为失败，返回 repeat 标记由前端自行提示
        data.put("repeat", result.repeat());
        data.put("points", result.points());
        data.put("continuousDays", signService.continuousDays(userId));
        data.put("balance", pointService.summary(userId).get("balance"));
        return Result.ok(data);
    }

    /**
     * 本月签到情况，页面画日历用
     */
    @GetMapping("/month")
    public Result<Map<String, Object>> month(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(signService.monthStatus(userId));
    }

    @GetMapping("/continuous")
    public Result<Integer> continuous(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(signService.continuousDays(userId));
    }

}
