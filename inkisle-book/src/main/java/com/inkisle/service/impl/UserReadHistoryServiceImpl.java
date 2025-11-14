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
        // 先看读到哪一章了，用来判断这次是不是真的往前读了。
        // 只读一次不会影响并发正确性：判重仍然交给 uk_user_book 的 upsert
        UserReadHistory exist = userReadHistoryMapper.selectByUserAndBook(history.getUserId(), history.getBookId());

        // 判重交给唯一键：并发首读时两个请求都查不到旧记录，
        // 先查后写会让后到的那个撞 uk_user_book 抛异常
        userReadHistoryMapper.upsert(history);

        // 只有真的换了一章才加热度。原来每调一次就加一次，
        // 同一章来回刷新就能把热度堆上去，榜单等于没有意义
        if (exist == null || !history.getChapterId().equals(exist.getChapterId())) {
            rankService.addHot(history.getBookId());
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
