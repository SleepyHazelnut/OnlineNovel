package com.inkisle.service.impl;

import com.inkisle.entity.BookInfo;
import com.inkisle.service.BookService;
import com.inkisle.service.RankService;
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
 * 热度榜放在 Redis 的 ZSet 里
 * <p>
 * 以前想按阅读量排就得 {@code ORDER BY read_count DESC}，每次都要扫全表再排序；
 * ZSet 本身就是按 score 排好的，取前 N 名是 O(log N + N)，加热度也是 O(log N)
 */
@Service
public class RankServiceImpl implements RankService {

    /** 榜单的 key，member 是书籍 id，score 是热度 */
    private static final String HOT_KEY = "rank:book:hot";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private BookService bookService;

    @Override
    public void addHot(Long bookId) {
        if (bookId == null) {
            return;
        }
        stringRedisTemplate.opsForZSet().incrementScore(HOT_KEY, String.valueOf(bookId), 1D);
    }

    @Override
    public List<Map<String, Object>> hotList(int top) {
        // 要的是从高到低，所以用 reverseRange
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(HOT_KEY, 0, (long) top - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> list = new ArrayList<>(tuples.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long bookId = Long.valueOf(tuple.getValue());
            // 走 BookService 拿，能吃到那层缓存
            BookInfo book = bookService.getById(bookId);
            if (book == null) {
                // 书被删了但榜单里还留着，跳过，别让前端收到空壳
                continue;
            }

            Map<String, Object> item = new HashMap<>();
            item.put("rank", rank++);
            item.put("bookId", bookId);
            item.put("bookName", book.getBookName());
            item.put("authorName", book.getAuthorName());
            item.put("coverUrl", book.getCoverUrl());
            item.put("heat", tuple.getScore());
            list.add(item);
        }
        return list;
    }

}
