package com.inkisle.service;

import com.inkisle.dto.BookVotesDTO;

import java.util.List;
import java.util.Map;

/**
 * 积分账本
 *
 * 账本放在用户服务：user 模块没有 openfeign 依赖，账本放书籍服务就要让
 * book -> user -> book 成环；而且「读了一章」发生在 book 的写链路里，
 * 两边都持本地事务跨服务同步写，一旦互等就是几十秒的锁等待
 */
public interface PointService {

    /**
     * 加分。返回 false 表示这次没记上账：来源重复、触发每日上限、账号太新
     * 都不抛异常——积分没加上不该让用户那一次评分/评论跟着失败
     */
    boolean award(Long userId, String bizType, Long bizId, Integer points, String remark);

    /**
     * 扣分。返回 false 表示余额不够；同一笔业务重复扣会抛业务异常，
     * 因为那是调用方的逻辑错误，不该静默吞掉
     */
    boolean spend(Long userId, String bizType, Long bizId, int points, String remark);

    /** 余额、累计值、等级、今日所得 */
    Map<String, Object> summary(Long userId);

    /** 明细分页 */
    Map<String, Object> ledger(Long userId, Integer pageNum, Integer pageSize);

    /** 规则表，给前端展示「怎么能赚分」 */
    List<Map<String, Object>> rules();

    /** 给一本书投荐书票，扣分并返回最新余额与票数 */
    Map<String, Object> recommend(Long userId, Long bookId);

    /** 票数最高的若干本 */
    List<BookVotesDTO> topRecommend(int limit);

    /** 对账：余额与流水求和不等的人 */
    List<Map<String, Object>> reconcile(int limit);

}
