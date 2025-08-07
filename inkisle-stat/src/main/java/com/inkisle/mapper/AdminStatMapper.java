package com.inkisle.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 看板统计跨四张表，单独放一个 Mapper */
@Mapper
public interface AdminStatMapper {

    Map<String, Object> selectTotal();

    /** 按天分组，没数据的日期不会出行，补零在服务层做 */
    List<Map<String, Object>> countUserByDay(@Param("days") Integer days);

    List<Map<String, Object>> countBookByDay(@Param("days") Integer days);

    List<Map<String, Object>> countCommentByDay(@Param("days") Integer days);

}
