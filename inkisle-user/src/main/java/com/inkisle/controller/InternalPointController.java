package com.inkisle.controller;

import com.inkisle.common.InternalControllerSupport;
import com.inkisle.dto.BookVotesDTO;
import com.inkisle.service.PointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 给书籍、审核两个服务上报积分用
 *
 * 接口一律幂等：加分的返回值是「这次有没有真的记上」，重复调用返回 false 而不是报错。
 * 上报是旁路，它失败不该把用户那次评分、评论、阅读一起打回去
 */
@RestController
@RequestMapping("/internal/point")
public class InternalPointController extends InternalControllerSupport {

    @Autowired
    private PointService pointService;

    @PostMapping("/award")
    public Boolean award(@RequestParam("userId") Long userId,
                         @RequestParam("bizType") String bizType,
                         @RequestParam("bizId") Long bizId,
                         @RequestParam(value = "points", required = false) Integer points,
                         @RequestParam(value = "remark", required = false) String remark) {
        return pointService.award(userId, bizType, bizId, points, remark);
    }

    @PostMapping("/spend")
    public Boolean spend(@RequestParam("userId") Long userId,
                         @RequestParam("bizType") String bizType,
                         @RequestParam("bizId") Long bizId,
                         @RequestParam("points") int points,
                         @RequestParam(value = "remark", required = false) String remark) {
        return pointService.spend(userId, bizType, bizId, points, remark);
    }

    @GetMapping("/top-recommend")
    public List<BookVotesDTO> topRecommend(@RequestParam(value = "limit", defaultValue = "6") int limit) {
        return pointService.topRecommend(limit);
    }

}
