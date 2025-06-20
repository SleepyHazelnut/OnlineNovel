package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.RankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 榜单，游客无需登录即可查看
 */
@RestController
@RequestMapping("/rank")
public class RankController {

    @Autowired
    private RankService rankService;

    /**
     * 热度榜，top 不传就取前 10
     */
    @GetMapping("/hot")
    public Result<List<Map<String, Object>>> hot(@RequestParam(defaultValue = "10") Integer top) {
        return Result.ok(rankService.hotList(top));
    }

}
