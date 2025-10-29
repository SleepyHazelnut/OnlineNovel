package com.inkisle.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserSignMapper {

    /**
     * 记一天签到
     * 同一天重复调用不该报错：签到判重在 Redis 里做，这里只是把结果落下来，
     * 万一 Lua 放行了两次或者重试补写，不该因为唯一键把接口打成 500
     */
    int insertIgnore(@Param("userId") Long userId,
                     @Param("signDate") LocalDate signDate,
                     @Param("createTime") LocalDateTime createTime);

    /** 某用户某个时间段内的签到日期，供位图重建 */
    List<LocalDate> selectDates(@Param("userId") Long userId,
                               @Param("from") LocalDate from,
                               @Param("to") LocalDate to);

}
