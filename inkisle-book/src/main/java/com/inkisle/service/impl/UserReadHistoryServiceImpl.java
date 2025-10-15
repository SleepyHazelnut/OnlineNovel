package com.inkisle.service.impl;

import com.inkisle.entity.UserReadHistory;
import com.inkisle.mapper.UserReadHistoryMapper;
import com.inkisle.service.RankService;
import com.inkisle.service.UserReadHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 阅读历史
 * 同一用户同一本书仅保留一条记录，靠 upsert 覆盖，不做先查后写
 */
@Service
public class UserReadHistoryServiceImpl implements UserReadHistoryService {

    @Autowired
    private UserReadHistoryMapper userReadHistoryMapper;

    @Autowired
    private RankService rankService;

    @Override
    public void save(UserReadHistory history) {
        // 判重交给唯一键：并发首读时两个请求都查不到旧记录，
        // 先查后写会让后到的那个撞 uk_user_book 抛异常
        userReadHistoryMapper.upsert(history);
        // 同时为该书增加热度，供排行榜使用
        rankService.addHot(history.getBookId());
    }

    @Override
    public List<UserReadHistory> listByUserId(Long userId) {
        return userReadHistoryMapper.selectByUserId(userId);
    }

    @Override
    public UserReadHistory lastRead(Long userId, Long bookId) {
        return userReadHistoryMapper.selectByUserAndBook(userId, bookId);
    }

}
