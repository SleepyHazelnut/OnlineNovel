package com.inkisle.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时把 Redis 里的阅读计数写回 book_info
 * 间隔取一分钟：热度榜是小时级的东西，没必要更勤；
 * 真要精确到秒，说明计数本身设计错了，该换成埋点
 */
@Slf4j
@Component
public class VisitCountFlusher {

    @Autowired
    private VisitCounter visitCounter;

    @Scheduled(fixedDelayString = "${inkisle.visit-count.flush-interval-ms:60000}",
            initialDelayString = "${inkisle.visit-count.flush-interval-ms:60000}")
    public void flush() {
        int written = visitCounter.flush();
        if (written > 0) {
            log.info("阅读计数回写完成，{} 本书", written);
        }
    }

}
