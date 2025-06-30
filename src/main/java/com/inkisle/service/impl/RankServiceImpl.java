package com.inkisle.service.impl;

import com.inkisle.common.BookAuditStatus;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookInfoMapper;
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
 * 榜单实现
 * <p>
 * 热度榜存放于 Redis 的 ZSet：按阅读量排序需全表扫描并排序，数据量增大后性能下降；
 * ZSet 按 score 自动排序，取前 N 名与增加热度均为对数级复杂度
 * <p>
 * 点击、新书、更新三个榜单读的是 book_info 上的列，配合索引直接倒序取前 N 条，
 * 不另存一份排行榜——那需要处理缓存与库的同步，而排序依据本身就在库里
 */
@Service
public class RankServiceImpl implements RankService {

    /** 榜单的 key：ZSet 里 member 是书籍 id，score 是热度 */
    private static final String HOT_KEY = "rank:book:hot";

    /** 一次最多取 100 条，避免调用方传入超大值把整张表拉回来 */
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
        // 榜单需由高到低排序，故使用 reverseRangeWithScores，普通 range 为升序
        Set<ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(HOT_KEY, 0, (long) size - 1);
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
            // 作品可能过审上榜后又被驳回或下架，榜单中该条同样不应显示
            // 直接用上面取到的实体判断：再调一次 isVisibleToReader 等于每本书多查一次库，
            // 榜单 10 条就是 10 次多余查询
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

    /**
     * SQL 的 LIMIT 不接受负数，top 传 0 或负数会让查询直接报错；
     * 传几千也没意义，这里统一收敛到 1-100
     */
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

    /**
     * 三个榜单共用的返回结构
     * 热度、创建时间、更新时间都带上，前端按当前页签决定显示哪一个，
     * 不必为每个榜单各写一套字段名
     */
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
