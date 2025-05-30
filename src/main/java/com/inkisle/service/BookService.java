package com.inkisle.service;

import com.inkisle.entity.BookInfo;

import java.util.Map;

public interface BookService {

    /**
     * 分页查询书籍
     */
    Map<String, Object> page(Integer pageNum, Integer pageSize);

    /**
     * 查询书籍详情
     */
    BookInfo getById(Long id);

}
