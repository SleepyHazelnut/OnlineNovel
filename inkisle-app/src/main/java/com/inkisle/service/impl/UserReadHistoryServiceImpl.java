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
 * 同一用户同一本书仅保留一条记录，已有记录时更新，不新增
 */
@Service
public class UserReadHistoryServiceImpl implements UserReadHistoryService {

    @Autowired
    private UserReadHistoryMapper userReadHistoryMapper;

    @Autowired
    private RankService rankService;

    @Override
    public void save(UserReadHistory history) {
        // 先查询该用户是否读过此书，未读过则插入，已读过则更新阅读位置
        UserReadHistory exists = userReadHistoryMapper.selectByUserAndBook(history.getUserId(), history.getBookId());
        if (exists == null) {
            userReadHistoryMapper.insert(history);
        } else {
            userReadHistoryMapper.update(history);
        }
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
