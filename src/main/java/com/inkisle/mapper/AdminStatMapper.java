package com.inkisle.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 管理端看板的统计查询
 * 统计跨 user、author、book、comment 四张表，归入任一实体的 Mapper 都不合适，故单独设立
 */
@Mapper
public interface AdminStatMapper {

    /**
     * 各项总数。四个子查询合并在一条 SQL 中，避免多次访问数据库；
     * resultType 直接使用 Map，一次性统计无需单独建实体类
     * 结果键名为 SQL 中的别名（如 userTotal），并非表列名
     */
    Map<String, Object> selectTotal();

    /**
     * 每天新增用户数，按 create_time 的日期分组，days 是往前看几天
     */
    List<Map<String, Object>> countUserByDay(@Param("days") Integer days);

    /**
     * 每天新增书籍数，除表名外与 countUserByDay 相同
     */
    List<Map<String, Object>> countBookByDay(@Param("days") Integer days);

    /**
     * 每天新增评论数。GROUP BY 产生的日期是稀疏的：无评论的日期不会出现对应行，
     * 补零由前端处理，SQL 不生成占位行
     */
    List<Map<String, Object>> countCommentByDay(@Param("days") Integer days);

}
