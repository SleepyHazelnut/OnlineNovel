package com.inkisle.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 作品维度的汇总统计，三个数分别来自阅读记录、收藏、评论三张表
 * 与书籍的增删改查职责不同，故单独设立 Mapper
 */
@Mapper
public interface BookStatMapper {

    /**
     * 一本书的阅读数、收藏数、评论数，一条 SQL 里三个子查询
     */
    Map<String, Object> selectBookStat(@Param("bookId") Long bookId);

}
