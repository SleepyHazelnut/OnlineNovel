package com.inkisle.client;

import com.inkisle.dto.BookVotesDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 积分账本在用户服务，书籍、审核两个服务只上报，不记账
 *
 * 为什么不把账本放书籍服务：user 模块的 pom 里没有 openfeign，
 * 反过来就要让 book -> user -> book 成环。更重要的是同一笔「读了一章」
 * 既在 book 的写链路里、又要写余额，两边都持本地事务，跨服务同步写
 * 一旦互相等就是 50 秒锁等待（编辑作品那条链路已经踩过一次）
 *
 * 所有返回值都是「这次有没有真的记上账」，重复调用返回 false，不抛异常：
 * 加分失败不该把用户的评分、阅读进度一起回滚
 */
@FeignClient(name = "inkisle-user", contextId = "pointClient")
public interface PointClient {

    /**
     * 加分。返回 false 表示这次没记（重复来源 / 触发每日上限 / 账号太新）
     *
     * @param points 为空时用 PointBizType 里的默认分值
     */
    @PostMapping("/internal/point/award")
    Boolean award(@RequestParam("userId") Long userId,
                  @RequestParam("bizType") String bizType,
                  @RequestParam("bizId") Long bizId,
                  @RequestParam(value = "points", required = false) Integer points,
                  @RequestParam(value = "remark", required = false) String remark);

    /**
     * 扣分。返回 false 表示余额不足或者这笔业务已经扣过
     * 余额在 SQL 里做条件更新，不会扣成负数
     */
    @PostMapping("/internal/point/spend")
    Boolean spend(@RequestParam("userId") Long userId,
                  @RequestParam("bizType") String bizType,
                  @RequestParam("bizId") Long bizId,
                  @RequestParam("points") Integer points,
                  @RequestParam(value = "remark", required = false) String remark);

    /** 票数最高的若干本，书籍服务据此拼首页板块 */
    @GetMapping("/internal/point/top-recommend")
    List<BookVotesDTO> topRecommend(@RequestParam("limit") int limit);

}
