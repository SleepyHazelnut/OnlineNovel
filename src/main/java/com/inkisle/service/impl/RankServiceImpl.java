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
 * 热度榜存放于 Redis 的 ZSet
 * <p>
 * 若按阅读量排序需 {@code ORDER BY read_count DESC}，每次全表扫描并排序，数据量增大后性能下降；
 * 改用 ZSet 后按 score 自动排序，取前 N 名与增加热度均为对数级复杂度
 */
@Service
public class RankServiceImpl implements RankService {

    /** 榜单的 key：ZSet 里 member 是书籍 id，score 是热度 */
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
        // 榜单需由高到低排序，故使用 reverseRangeWithScores，普通 range 为升序
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(HOT_KEY, 0, (long) top - 1);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> list = new ArrayList<>(tuples.size());
        int rank = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long bookId = Long.valueOf(tuple.getValue());
            // 通过 BookService 获取，可复用其缓存
            BookInfo book = bookService.getById(bookId);
            if (book == null) {
                // 作品已删除但榜单仍有残留记录时跳过，避免前端返回空数据
                continue;
            }
            // 作品可能过审上榜后又被驳回下架，榜单中该条同样不应显示
            if (!bookService.isVisibleToReader(bookId)) {
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
