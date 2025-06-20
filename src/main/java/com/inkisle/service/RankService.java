package com.inkisle.service;

import java.util.List;
import java.util.Map;

/**
 * 书籍热度榜，数据存放于 Redis 的 ZSet
 */
public interface RankService {

    /**
     * 给某本书增加热度，作品被阅读时调用
     */
    void addHot(Long bookId);

    /**
     * 热度前 N 名，由高到低排序
     */
    List<Map<String, Object>> hotList(int top);

}
