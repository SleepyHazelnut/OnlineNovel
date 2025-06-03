package com.inkisle.mapper;

import com.inkisle.entity.UserReadHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserReadHistoryMapper {

    UserReadHistory selectByUserAndBook(@Param("userId") Long userId, @Param("bookId") Long bookId);

    // 首次阅读该书时插入一条记录，再次阅读由下方 update 覆盖
    int insert(UserReadHistory history);

    /**
     * 更新阅读进度。同一 user + book 仅保留一条记录，进度直接覆盖旧值
     */
    int update(UserReadHistory history);

    /**
     * 某用户的全部阅读记录，联表带出书名，最近阅读的在前
     */
    List<UserReadHistory> selectByUserId(@Param("userId") Long userId);

}
