package com.inkisle.service.impl;

import com.inkisle.entity.BookChapter;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.service.BookChapterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookChapterServiceImpl implements BookChapterService {

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Override
    public List<BookChapter> listByBookId(Long bookId) {
        return bookChapterMapper.selectListByBookId(bookId);
    }

    @Override
    public Map<String, Object> read(Long id) {
        BookChapter chapter = bookChapterMapper.selectById(id);
        if (chapter == null) {
            return null;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("chapter", chapter);
        data.put("prevId", bookChapterMapper.selectPrevId(chapter.getBookId(), chapter.getChapterNum()));
        data.put("nextId", bookChapterMapper.selectNextId(chapter.getBookId(), chapter.getChapterNum()));
        return data;
    }

}
