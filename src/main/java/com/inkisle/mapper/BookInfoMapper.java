package com.inkisle.mapper;

import com.inkisle.entity.BookInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookInfoMapper {

    /**
     * 分页查询，由上层算出 offset 传进来；categoryId 为空时查全部
     */
    List<BookInfo> selectPage(@Param("offset") int offset,
                              @Param("pageSize") int pageSize,
                              @Param("categoryId") Long categoryId);

    int countByCategory(@Param("categoryId") Long categoryId);

    BookInfo selectById(@Param("id") Long id);

    /**
     * 按书名或作者模糊搜索
     */
    List<BookInfo> searchByKeyword(@Param("offset") int offset,
                                   @Param("pageSize") int pageSize,
                                   @Param("keyword") String keyword);

    int countByKeyword(@Param("keyword") String keyword);

}
