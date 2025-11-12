package com.inkisle.mapper;

import com.inkisle.entity.UserPointLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserPointLedgerMapper {

    /**
     * 记一笔流水，唯一键撞了就当没发生
     * 返回影响行数：1 是这次真记上了，0 是这条业务已经记过
     */
    int insertIgnore(UserPointLedger ledger);

    /** 明细列表，按时间倒序翻页 */
    List<UserPointLedger> selectPage(@Param("userId") Long userId,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    int countByUser(@Param("userId") Long userId);

    /** 今天这个来源记了几条，Redis 挂掉时的兜底计数 */
    int countByTypeSince(@Param("userId") Long userId,
                         @Param("bizType") String bizType,
                         @Param("from") LocalDateTime from);

    /** 今天赚了多少分（只算正的，消耗不算「今日获得」） */
    int sumEarnedSince(@Param("userId") Long userId,
                       @Param("from") LocalDateTime from);

    /**
     * 对账：余额和流水求和对不上的用户
     * 两边都是同一批数据的表述，差一分都说明有一次写只成功了一半
     */
    List<Map<String, Object>> selectMismatch(@Param("limit") int limit);

}
