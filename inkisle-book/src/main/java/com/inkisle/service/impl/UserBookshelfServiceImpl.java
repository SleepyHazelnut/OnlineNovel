package com.inkisle.service.impl;

import com.inkisle.entity.UserBookshelf;
import com.inkisle.mapper.UserBookshelfMapper;
import com.inkisle.service.UserBookshelfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 书架（收藏）
 * 用户与作品为多对多关系，中间表一条记录即一次收藏
 * 新增前先查询是否已存在，不存在才插入，避免重复收藏
 */
@Service
public class UserBookshelfServiceImpl implements UserBookshelfService {

    @Autowired
    private UserBookshelfMapper userBookshelfMapper;

    @Override
    public void add(Long userId, Long bookId) {
        // 加入书架按钮可能被连续点击，已收藏时直接返回
        if (userBookshelfMapper.countByUserAndBook(userId, bookId) > 0) {
            return;
        }
        UserBookshelf bookshelf = new UserBookshelf();
        bookshelf.setUserId(userId);
        bookshelf.setBookId(bookId);
        userBookshelfMapper.insert(bookshelf);
    }

    @Override
    public void remove(Long userId, Long bookId) {
        userBookshelfMapper.deleteByUserAndBook(userId, bookId);
    }

    @Override
    public List<UserBookshelf> listByUserId(Long userId) {
        return userBookshelfMapper.selectByUserId(userId);
    }

    @Override
    public boolean isCollected(Long userId, Long bookId) {
        return userBookshelfMapper.countByUserAndBook(userId, bookId) > 0;
    }

}
