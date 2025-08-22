package com.inkisle.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 作品维度的汇总，三个数来自阅读记录、收藏、评论三张表
 * 跟书的增删改查不是一回事，单独开一个
 */
@Mapper
public interface BookStatMapper {

    /**
     * 一本书的阅读数、收藏数、评论数，一条 SQL 里三个子查询
     */
    Map<String, Object> selectBookStat(@Param("bookId") Long bookId);

}
