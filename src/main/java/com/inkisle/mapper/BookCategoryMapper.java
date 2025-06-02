package com.inkisle.mapper;

import com.inkisle.entity.BookCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BookCategoryMapper {

    /**
     * 查询全部分类，按 sort 升序。分类为初始化时一次性写入、变动很少，
     * 故不分页也不加过滤条件
     */
    List<BookCategory> selectAll();

}
