package com.inkisle.service.impl;

import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookServiceImpl implements BookService {

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Override
    public Map<String, Object> page(Integer pageNum, Integer pageSize, Long categoryId) {
        int offset = (pageNum - 1) * pageSize;
        List<BookInfo> list = bookInfoMapper.selectPage(offset, pageSize, categoryId);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", bookInfoMapper.countByCategory(categoryId));
        return data;
    }

    @Override
    public BookInfo getById(Long id) {
        return bookInfoMapper.selectById(id);
    }

    @Override
    public Map<String, Object> search(Integer pageNum, Integer pageSize, String keyword) {
        int offset = (pageNum - 1) * pageSize;
        List<BookInfo> list = bookInfoMapper.searchByKeyword(offset, pageSize, keyword);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", bookInfoMapper.countByKeyword(keyword));
        return data;
    }

}
