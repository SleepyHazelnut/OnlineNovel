package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.HomeBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 读者端首页推荐位，游客可访问
 */
@RestController
@RequestMapping("/home")
public class HomeController {

    @Autowired
    private HomeBookService homeBookService;

    /**
     * 三个板块：热门推荐、精品推荐、最新更新
     */
    @GetMapping("/recommend")
    public Result<Map<String, Object>> recommend() {
        return Result.ok(homeBookService.recommend());
    }

}
