package com.inkisle.service;

import com.inkisle.entity.BookInfo;

import java.util.Map;

public interface BookService {

    /**
     * 书籍分页列表，categoryId 为空时返回全部
     */
    Map<String, Object> page(Integer pageNum, Integer pageSize, Long categoryId);

    BookInfo getById(Long id);

    /**
     * 搜索书籍，书名或作者命中即可
     */
    Map<String, Object> search(Integer pageNum, Integer pageSize, String keyword);

}
