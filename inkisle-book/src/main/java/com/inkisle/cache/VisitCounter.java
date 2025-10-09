package com.inkisle.cache;

import com.inkisle.mapper.BookInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阅读计数
 * 原来是 GET /chapter/{id} 里直接 UPDATE visit_count + 1，热门书的所有读者会在同一行上排队，
 * 而且读接口不该写库：预取、重试、爬虫都会把这个数刷高。
 * 现在先在 Redis 里按书累加，定时批量回写，读接口只读不写。
 *
 * 计数是统计值，不是账：Redis 丢了最多丢一小段增量，
 * 不值得为它上事务或补偿队列，所以写失败就退回直接写库，绝不因此让读者打不开章节
 */
@Slf4j
@Component
public class VisitCounter {

    /** 待回写：field 是书 id，value 是本轮累计次数 */
    private static final String PENDING_KEY = "book:visit:pending";

    /** 单条 UPDATE 里最多带多少个 id，太长会撑爆预处理语句 */
    private static final int BATCH_SIZE = 200;

    /**
     * 取走全部增量并删除，两步放一条脚本里。
     * 先 HGETALL 再 DEL 会漏掉这中间进来的计数
     */
    private static final DefaultRedisScript<List> TAKE_ALL = new DefaultRedisScript<>(
            "local data = redis.call('HGETALL', KEYS[1]) "
                    + "if #data > 0 then redis.call('DEL', KEYS[1]) end "
                    + "return data",
            List.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    /**
     * redis：先累加再定时落库，读接口不碰数据库
     * database：直接写库，给没有 Redis 的测试环境兜底
     */
    @Value("${inkisle.visit-count.mode:redis}")
    private String mode;

    /** 记一次阅读。任何异常都不能往上抛，计数丢了是小事，章节打不开是大事 */
    public void record(Long bookId) {
        if (bookId == null) {
            return;
        }
        if (!"redis".equalsIgnoreCase(mode)) {
            bookInfoMapper.incrementVisitCount(bookId);
            return;
        }
        try {
            stringRedisTemplate.opsForHash().increment(PENDING_KEY, String.valueOf(bookId), 1);
        } catch (Exception e) {
            log.warn("阅读计数写入 Redis 失败，本次直接写库 bookId={}", bookId, e);
            bookInfoMapper.incrementVisitCount(bookId);
        }
    }

    /**
     * 把当前累计写回 book_info
     *
     * @return 回写的书籍数，0 表示没有待处理增量
     */
    public int flush() {
        Map<Long, Long> pending = take();
        if (pending.isEmpty()) {
            return 0;
        }

        List<Map.Entry<Long, Long>> entries = new ArrayList<>(pending.entrySet());
        int written = 0;
        for (int from = 0; from < entries.size(); from += BATCH_SIZE) {
            List<Map.Entry<Long, Long>> slice = entries.subList(from, Math.min(from + BATCH_SIZE, entries.size()));
            Map<Long, Long> batch = new HashMap<>(slice.size());
            slice.forEach(entry -> batch.put(entry.getKey(), entry.getValue()));
            try {
                bookInfoMapper.incrementVisitCountBatch(batch);
                written += batch.size();
            } catch (Exception e) {
                // 这一批写不进去就还回 Redis，等下一轮再试；还回去失败只能认丢，计数不值得重试到死
                requeue(batch);
                log.warn("阅读计数回写失败，已退回 Redis，本批 {} 本", batch.size(), e);
            }
        }
        return written;
    }

    /** 取走全部增量，失败时返回空表，下一轮再来 */
    private Map<Long, Long> take() {
        List<?> raw;
        try {
            raw = stringRedisTemplate.execute(TAKE_ALL, List.of(PENDING_KEY));
        } catch (Exception e) {
            log.warn("读取待回写的阅读计数失败，本轮跳过", e);
            return Map.of();
        }
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> result = new HashMap<>(raw.size() / 2);
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            try {
                result.put(Long.valueOf(String.valueOf(raw.get(i))), Long.valueOf(String.valueOf(raw.get(i + 1))));
            } catch (NumberFormatException e) {
                log.warn("阅读计数出现非法记录 key={} value={}", raw.get(i), raw.get(i + 1));
            }
        }
        return result;
    }

    private void requeue(Map<Long, Long> batch) {
        try {
            batch.forEach((bookId, delta) ->
                    stringRedisTemplate.opsForHash().increment(PENDING_KEY, String.valueOf(bookId), delta));
        } catch (Exception e) {
            log.warn("阅读计数退回 Redis 也失败了，这部分增量丢失：{} 本", batch.size(), e);
        }
    }

}
