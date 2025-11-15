package com.inkisle.service.impl;

import com.inkisle.cache.CacheInvalidator;
import com.inkisle.cache.CacheNames;
import com.inkisle.common.BusinessException;
import com.inkisle.common.PointBizType;
import com.inkisle.entity.BookRating;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.BookRatingMapper;
import com.inkisle.service.BookRatingService;
import com.inkisle.service.BookService;
import com.inkisle.service.support.PointReporter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 评分
 * 评分表只记每人打的原始分，平均分每次算完写回 book_info，
 * 列表和榜单直接读列，不用每本书都带一个 AVG 子查询
 */
@Service
public class BookRatingServiceImpl implements BookRatingService {

    @Autowired
    private BookRatingMapper bookRatingMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private BookService bookService;

    @Autowired
    private CacheInvalidator cacheInvalidator;

    @Autowired
    private PointReporter pointReporter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rate(Long bookId, Long userId, Integer score) {
        if (score == null || score < 1 || score > 5) {
            throw new BusinessException("评分只能是 1-5 星");
        }
        // 只能给上架的书打分。没审过的读者本来就看不到，但直接拿 id 调这个接口还是能写进去
        if (!bookService.isVisibleToReader(bookId)) {
            throw new BusinessException("作品不存在或未上架");
        }

        LocalDateTime now = LocalDateTime.now();
        // 改分不算新的一次评价，所以先看有没有打过分。
        // 并发首评时两个请求都会看到 null，两个都上报，最后靠唯一键只留一条
        boolean firstTime = bookRatingMapper.selectByBookAndUser(bookId, userId) == null;

        BookRating rating = new BookRating();
        rating.setBookId(bookId);
        rating.setUserId(userId);
        rating.setScore(score);
        rating.setCreateTime(now);
        rating.setUpdateTime(now);
        bookRatingMapper.upsert(rating);

        if (firstTime) {
            // afterCommit：别在事务里发同步调用，持锁时间等于一次 HTTP 往返
            pointReporter.awardAfterCommit(userId, PointBizType.RATING, bookId, null, "评了个分");
        }

        Map<String, Object> data = refreshBookScore(bookId);
        data.put("myScore", score);
        return data;
    }

    @Override
    public Map<String, Object> summary(Long bookId, Long userId) {
        Map<String, Object> data = aggregate(bookId);
        Integer myScore = null;
        if (userId != null) {
            BookRating mine = bookRatingMapper.selectByBookAndUser(bookId, userId);
            myScore = mine == null ? null : mine.getScore();
        }
        data.put("myScore", myScore);
        return data;
    }

    /**
     * 重算后写回 book_info 两列，同时清除详情缓存
     * 聚合放在 SQL 里（BookInfoMapper.refreshRating），不在应用层先查再写，
     * 两人同时打分时，各自基于旧快照计算会互相覆盖
     */
    private Map<String, Object> refreshBookScore(Long bookId) {
        bookInfoMapper.refreshRating(bookId);
        Map<String, Object> data = aggregate(bookId);

        // 事务提交后再清，否则删完到提交之间进来的读请求会把旧评分写回缓存
        cacheInvalidator.evict(CacheNames.BOOK, bookId);
        return data;
    }

    /**
     * 平均分留一位小数。没人评分就返回 0.0 和 0，
     * 别让 null 一路传到前端显示成 null 分
     */
    private Map<String, Object> aggregate(Long bookId) {
        Map<String, Object> row = bookRatingMapper.selectSummary(bookId);

        BigDecimal score = BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        int count = 0;
        if (row != null) {
            Object avg = row.get("avgScore");
            if (avg != null) {
                score = new BigDecimal(avg.toString()).setScale(1, RoundingMode.HALF_UP);
            }
            Object total = row.get("ratingCount");
            if (total != null) {
                count = Integer.parseInt(total.toString());
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("score", score);
        data.put("ratingCount", count);
        return data;
    }

}
