package com.inkisle.mapper;

import com.inkisle.entity.BookInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookInfoMapper {

    /**
     * 分页查询，由上层算出 offset 传进来
     */
    List<BookInfo> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);

    int countAll();

    BookInfo selectById(@Param("id") Long id);

}
