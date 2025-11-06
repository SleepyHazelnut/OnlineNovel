package com.inkisle.mapper;

import com.inkisle.entity.HomeBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface HomeBookMapper {

    /**
     * 管理端列表：该板块的全部条目，含已下架、已驳回的书
     * 管理员需要看到这些条目，否则书被下架后推荐位里会留一条看不见也删不掉的记录
     */
    List<Map<String, Object>> selectBySection(@Param("section") Integer section);

    /**
     * 读者端列表：只取已上架的书，未过审与已下架的不展示
     */
    List<Map<String, Object>> selectVisibleBySection(@Param("section") Integer section);

    int countBySectionAndBook(@Param("section") Integer section, @Param("bookId") Long bookId);

    int insert(HomeBook homeBook);

    /** 同板块同书只留一条，重复配置时返回 0 */
    int insertIgnore(HomeBook homeBook);

    int updateSort(@Param("id") Long id, @Param("sort") Integer sort);

    int deleteById(@Param("id") Long id);

    /**
     * 删除某本书的全部推荐位，作品被删时一并清理
     */
    int deleteByBookId(@Param("bookId") Long bookId);

}
