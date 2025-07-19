package com.inkisle.service.impl;

import com.inkisle.common.BookAuditStatus;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookService;
import com.inkisle.service.RankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 热度榜放 Redis ZSet；其余三个榜直接读 book_info 上的列，不另存一份排行
 */
@Slf4j
@Service
public class RankServiceImpl implements RankService {

    private static final String HOT_KEY = "rank:book:hot";

    private static final int MAX_TOP = 100;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Override
    public void addHot(Long bookId) {
        if (bookId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().incrementScore(HOT_KEY, String.valueOf(bookId), 1D);
    }

    @Override
    public List<Map<String, Object>> hotList(int top) {
        int size = clampTop(top);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(HOT_KEY, 0, (long) size - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> list = new ArrayList<>(tuples.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            if (member == null) {
                continue;
            }
            long bookId;
            try {
                bookId = Long.parseLong(member);
            } catch (NumberFormatException e) {
                // ZSet 里混进非数字成员时跳过这一条，不该让整个榜单 500
                log.warn("热度榜出现非法成员 member={}", member);
                continue;
            }
            BookInfo book = bookService.getById(bookId);
            if (book == null) {
                // 书删了但 ZSet 里的记录还在
                continue;
            }
            // 下架或驳回的书不该留在榜上。直接用查出来的实体判断，不再多查一次库
            if (!BookAuditStatus.isVisible(book.getAuditStatus())) {
                continue;
            }
            list.add(toItem(rank++, book, tuple.getScore()));
        }
        return list;
    }

    @Override
    public List<Map<String, Object>> clickList(int top) {
        return toRankList(bookInfoMapper.selectTopByVisit(clampTop(top)));
    }

    @Override
    public List<Map<String, Object>> newList(int top) {
        return toRankList(bookInfoMapper.selectTopByCreateTime(clampTop(top)));
    }

    @Override
    public List<Map<String, Object>> updateList(int top) {
        return toRankList(bookInfoMapper.selectTopByUpdateTime(clampTop(top)));
    }

    /** LIMIT 不接受 0 和负数，统一收到 1-100 */
    private int clampTop(int top) {
        return Math.max(1, Math.min(top, MAX_TOP));
    }

    private List<Map<String, Object>> toRankList(List<BookInfo> books) {
        List<Map<String, Object>> list = new ArrayList<>(books.size());
        int rank = 1;
        for (BookInfo book : books) {
            list.add(toItem(rank++, book, book.getVisitCount() == null ? 0D : book.getVisitCount().doubleValue()));
        }
        return list;
    }

    /** 四个榜共用一个返回结构，前端按页签取对应字段 */
    private Map<String, Object> toItem(int rank, BookInfo book, Double heat) {
        Map<String, Object> item = new HashMap<>();
        item.put("rank", rank);
        item.put("bookId", book.getId());
        item.put("bookName", book.getBookName());
        item.put("authorName", book.getAuthorName());
        item.put("coverUrl", book.getCoverUrl());
        item.put("heat", heat == null ? 0D : heat);
        item.put("wordCount", book.getWordCount());
        item.put("score", book.getScore());
        item.put("createTime", book.getCreateTime());
        item.put("updateTime", book.getUpdateTime());
        return item;
    }

}
