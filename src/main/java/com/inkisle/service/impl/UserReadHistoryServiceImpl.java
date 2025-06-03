package com.inkisle.service.impl;

import com.inkisle.entity.UserReadHistory;
import com.inkisle.mapper.UserReadHistoryMapper;
import com.inkisle.service.UserReadHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserReadHistoryServiceImpl implements UserReadHistoryService {

    @Autowired
    private UserReadHistoryMapper userReadHistoryMapper;

    @Override
    public void save(UserReadHistory history) {
        UserReadHistory exists = userReadHistoryMapper.selectByUserAndBook(history.getUserId(), history.getBookId());
        if (exists == null) {
            userReadHistoryMapper.insert(history);
        } else {
            userReadHistoryMapper.update(history);
        }
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
