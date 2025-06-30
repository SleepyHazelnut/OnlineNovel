package com.inkisle.service;

import java.util.List;
import java.util.Map;

/**
 * 榜单
 * 热度榜放在 Redis 的 ZSet 里，点击、新书、更新三个榜单直接查库：
 * 三者都按 book_info 上已有的列排序，加索引后取前 N 条不必全表排序，
 * 再引入一套缓存反而多一份要同步的数据
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

    /**
     * 点击榜：按累计阅读次数排序
     */
    List<Map<String, Object>> clickList(int top);

    /**
     * 新书榜：按创建时间倒序
     */
    List<Map<String, Object>> newList(int top);

    /**
     * 更新榜：按最近更新时间倒序，有新章节的书排前面
     */
    List<Map<String, Object>> updateList(int top);

}
