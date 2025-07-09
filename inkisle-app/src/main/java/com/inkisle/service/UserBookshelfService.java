package com.inkisle.service;

import com.inkisle.entity.UserBookshelf;

import java.util.List;

public interface UserBookshelfService {

    /**
     * 加入书架，已收藏时直接返回，不重复插入
     */
    void add(Long userId, Long bookId);

    void remove(Long userId, Long bookId);

    List<UserBookshelf> listByUserId(Long userId);

    boolean isCollected(Long userId, Long bookId);

}
