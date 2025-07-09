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
     * 热度榜：阅读时实时累加的热度，存放于 Redis
     */
    @GetMapping("/hot")
    public Result<List<Map<String, Object>>> hot(@RequestParam(defaultValue = "20") Integer top) {
        return Result.ok(rankService.hotList(top));
    }

    /**
     * 点击榜：按累计阅读次数排序，不依赖 Redis
     */
    @GetMapping("/click")
    public Result<List<Map<String, Object>>> click(@RequestParam(defaultValue = "20") Integer top) {
        return Result.ok(rankService.clickList(top));
    }

    /**
     * 新书榜：按创建时间倒序
     */
    @GetMapping("/new")
    public Result<List<Map<String, Object>>> newest(@RequestParam(defaultValue = "20") Integer top) {
        return Result.ok(rankService.newList(top));
    }

    /**
     * 更新榜：按最近更新时间倒序
     */
    @GetMapping("/update")
    public Result<List<Map<String, Object>>> update(@RequestParam(defaultValue = "20") Integer top) {
        return Result.ok(rankService.updateList(top));
    }

}
