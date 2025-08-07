package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.AdminStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端看板，为后台首页统计卡片提供数据
 */
@RestController
@RequestMapping("/admin/stat")
public class AdminStatController {

    @Autowired
    private AdminStatService adminStatService;

    /**
     * 总量统计：用户 / 作者 / 书籍 / 评论
     */
    @GetMapping("/total")
    public Result<Map<String, Object>> total() {
        return Result.ok(adminStatService.total());
    }

    /**
     * 按天趋势，days 不传就取最近 7 天
     */
    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> daily(@RequestParam(defaultValue = "7") Integer days) {
        return Result.ok(adminStatService.daily(days));
    }

}
