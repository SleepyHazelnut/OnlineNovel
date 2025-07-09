package com.inkisle.service;

import java.util.List;
import java.util.Map;

/**
 * 热度榜走 Redis ZSet，其余三个榜直接按 book_info 的列排序查库
 */
public interface RankService {

    void addHot(Long bookId);

    List<Map<String, Object>> hotList(int top);

    List<Map<String, Object>> clickList(int top);

    List<Map<String, Object>> newList(int top);

    List<Map<String, Object>> updateList(int top);

}
