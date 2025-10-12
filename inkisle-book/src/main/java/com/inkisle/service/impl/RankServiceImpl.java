package com.inkisle.service.impl;

import com.inkisle.common.BookAuditStatus;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.RankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 热度榜放 Redis ZSet，另外三个榜直接按 book_info 的列排，没另存一份排行
 *
 * 热度按 ISO 周分桶：早期是全年一个 key 一路累加，分数只增不减，
 * 「本周强推」实际排的是历史总阅读量，一本老书压在上面永远下不来。
 * 现在每周一个 key，查的时候把本周和上周合起来看 —— 只取本周，
 * 周一凌晨榜会是空的，跨周那一下体验最差
 */
@Slf4j
@Service
public class RankServiceImpl implements RankService {

    private static final String HOT_KEY_PREFIX = "rank:book:hot:";

    /** 留两周多一点：上周的榜还要参与合并，过了就没人再读 */
    private static final Duration WEEK_TTL = Duration.ofDays(15);

    private static final int MAX_TOP = 100;

    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Override
    public void addHot(Long bookId) {
        if (bookId == null) {
            return;
        }
        String key = weekKey(0);
        try {
            Double score = stringRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(bookId), 1D);
            // 成员第一次出现时返回 1.0，此时 key 刚被创建，顺手把过期时间挂上。
            // 每周都刷一次 EXPIRE 也行，但那是每次阅读多一次往返
            if (score != null && score == 1D) {
                stringRedisTemplate.expire(key, WEEK_TTL);
            }
        } catch (Exception e) {
            // 热度是统计，写不进去不该让「保存阅读进度」失败
            log.warn("热度累加失败，本次阅读不计入榜单 bookId={}", bookId, e);
        }
    }

    @Override
    public List<Map<String, Object>> hotList(int top) {
        int size = clampTop(top);
        Map<Long, Double> merged = mergeWeekly(size);
        if (merged.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<Long, Double>> ordered = new ArrayList<>(merged.entrySet());
        ordered.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // 一次把候选书全查出来，不再逐本查库
        List<Long> ids = ordered.stream().map(Map.Entry::getKey).toList();
        Map<Long, BookInfo> books = bookInfoMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(BookInfo::getId, book -> book));

        List<Map<String, Object>> list = new ArrayList<>(size);
        for (Map.Entry<Long, Double> entry : ordered) {
            if (list.size() >= size) {
                break;
            }
            BookInfo book = books.get(entry.getKey());
            // 书删了但 ZSet 里的成员还在，等这一周的 key 过期就自然没了
            if (book == null) {
                continue;
            }
            // 下架或驳回的书不该留在榜上
            if (!BookAuditStatus.isVisible(book.getAuditStatus())) {
                continue;
            }
            list.add(toItem(list.size() + 1, book, entry.getValue()));
        }
        return list;
    }

    /**
     * 本周与上周的分数相加
     * 多取一倍成员：两周各有各的头部，合并后可能挤掉一部分，取 size * 2 足够覆盖
     */
    private Map<Long, Double> mergeWeekly(int size) {
        Map<Long, Double> merged = new HashMap<>();
        for (int weeksBack = 0; weeksBack <= 1; weeksBack++) {
            collect(weekKey(weeksBack), size * 2, merged);
        }
        return merged;
    }

    private void collect(String key, int limit, Map<Long, Double> into) {
        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, (long) limit - 1);
        } catch (Exception e) {
            // Redis 不可用时热度榜空着，其余三榜仍然能用
            log.warn("读取热度榜失败 key={}", key, e);
            return;
        }
        if (tuples == null || tuples.isEmpty()) {
            return;
        }
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            if (member == null) {
                continue;
            }
            try {
                long bookId = Long.parseLong(member);
                into.merge(bookId, tuple.getScore() == null ? 0D : tuple.getScore(), Double::sum);
            } catch (NumberFormatException e) {
                // ZSet 里混进非数字成员时跳过，不该让整个榜单 500
                log.warn("热度榜出现非法成员 member={}", member);
            }
        }
    }

    /** ISO 周：跨年那几天按「周所属的年」算，否则 12 月 31 日和 1 月 1 日会落进同一个 key */
    private String weekKey(int weeksBack) {
        LocalDate date = LocalDate.now().minusWeeks(weeksBack);
        int year = date.get(ISO_WEEK.weekBasedYear());
        int week = date.get(ISO_WEEK.weekOfWeekBasedYear());
        return HOT_KEY_PREFIX + year + "W" + String.format("%02d", week);
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
