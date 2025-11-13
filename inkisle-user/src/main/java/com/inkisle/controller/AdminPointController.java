package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.PointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 积分对账
 *
 * 余额是流水的物化结果，两者对不上说明有一次写只成功了一半：
 * 要么流水记了余额没动，要么余额动了流水没记。
 * 这种偏差不会自己消失，也不会报错，只能靠定时对账发现
 */
@RestController
@RequestMapping("/admin/point")
public class AdminPointController {

    @Autowired
    private PointService pointService;

    /**
     * 列出余额与流水求和不一致的账户
     * 空列表才是正常结果；非空就要人工介入，别写自动修正——
     * 自动修正等于用一个猜测覆盖另一个猜测，真因会被永久掩盖
     */
    @GetMapping("/reconcile")
    public Result<List<Map<String, Object>>> reconcile(@RequestParam(required = false) Integer limit) {
        return Result.ok(pointService.reconcile(limit == null ? 20 : limit));
    }

}
