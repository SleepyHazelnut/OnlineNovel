package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookRating;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.BookRatingMapper;
import com.inkisle.service.BookRatingService;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 评分
 * 评分表只存每人的原始分数，平均分在每次评分后算一次并写回 book_info，
 * 列表与榜单读列即可，避免每本书都带一个 AVG 子查询
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
    private CacheManager cacheManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rate(Long bookId, Long userId, Integer score) {
        if (score == null || score < 1 || score > 5) {
            throw new BusinessException("评分只能是 1-5 星");
        }
        // 只有上架作品可评分：未过审的书读者本就看不到，
        // 但仅凭 id 直接调这个接口仍能写进评分
        if (!bookService.isVisibleToReader(bookId)) {
            throw new BusinessException("作品不存在或未上架");
        }

        LocalDateTime now = LocalDateTime.now();
        BookRating rating = new BookRating();
        rating.setBookId(bookId);
        rating.setUserId(userId);
        rating.setScore(score);
        rating.setCreateTime(now);
        rating.setUpdateTime(now);
        bookRatingMapper.upsert(rating);

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
     * 重算并写回 book_info 的两列，同时清掉详情缓存
     * 聚合放在 SQL 里做（见 BookInfoMapper.refreshRating），不在这里先查再写，
     * 否则并发打分时两个事务会拿着各自的旧快照互相覆盖
     * 不清缓存的话详情页最长一个缓存周期内还是旧分数
     */
    private Map<String, Object> refreshBookScore(Long bookId) {
        bookInfoMapper.refreshRating(bookId);
        Map<String, Object> data = aggregate(bookId);

        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(bookId);
        }
        return data;
    }

    /**
     * 平均分保留一位小数，四舍五入。没人评分时明确返回 0.0 与 0，
     * 而不是让 null 一路传到前端显示成「null 分」
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
