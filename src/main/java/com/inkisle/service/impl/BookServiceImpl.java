package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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

    @Override
    public List<BookInfo> listByAuthor(Long authorId) {
        return bookInfoMapper.selectByAuthorId(authorId);
    }

    @Override
    public void save(BookInfo bookInfo, Long authorId) {
        if (bookInfo.getId() == null) {
            bookInfo.setAuthorId(authorId);
            // 新建默认连载中
            bookInfo.setStatus(0);
            bookInfo.setCreateTime(LocalDateTime.now());
            bookInfoMapper.insert(bookInfo);
            return;
        }

        BookInfo exist = bookInfoMapper.selectById(bookInfo.getId());
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        // 这里先手写判断，后面几个作者接口都要用，迟早得抽出去
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能修改自己的作品");
        }
        bookInfo.setAuthorId(authorId);
        bookInfo.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(bookInfo);
    }

    @Override
    public void delete(Long id, Long authorId) {
        BookInfo exist = bookInfoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能删除自己的作品");
        }
        bookInfoMapper.deleteById(id);
    }

}
