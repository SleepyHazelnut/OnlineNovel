package com.inkisle.service;

import com.inkisle.entity.UserReadHistory;

import java.util.List;

public interface UserReadHistoryService {

    /**
     * 记录阅读位置，同一用户同一本书仅保留最新一条
     */
    void save(UserReadHistory history);

    List<UserReadHistory> listByUserId(Long userId);

    UserReadHistory lastRead(Long userId, Long bookId);

}
