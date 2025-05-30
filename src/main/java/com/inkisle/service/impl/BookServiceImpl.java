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
    public Map<String, Object> page(Integer pageNum, Integer pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<BookInfo> list = bookInfoMapper.selectPage(offset, pageSize);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", bookInfoMapper.countAll());
        return data;
    }

    @Override
    public BookInfo getById(Long id) {
        return bookInfoMapper.selectById(id);
    }

}
