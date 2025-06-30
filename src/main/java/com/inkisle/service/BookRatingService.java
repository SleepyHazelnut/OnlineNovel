package com.inkisle.service;

import java.util.Map;

/**
 * 书籍评分
 * book_info 上的 score / rating_count 是评分表的聚合结果，
 * 每次评分后覆盖更新，详情页与列表直接读列，不再实时聚合
 */
public interface BookRatingService {

    /**
     * 提交评分，1-5 星。同一读者重复评分覆盖旧值，返回最新聚合结果
     */
    Map<String, Object> rate(Long bookId, Long userId, Integer score);

    /**
     * 评分汇总：平均分、人数，以及当前读者的评分（未评过为 null）
     */
    Map<String, Object> summary(Long bookId, Long userId);

}
