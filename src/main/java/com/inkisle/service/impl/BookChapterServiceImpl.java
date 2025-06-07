package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
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

    @Autowired
    private BookContentMapper bookContentMapper;

    @Override
    public List<BookChapter> listByBookId(Long bookId) {
        return bookChapterMapper.selectListByBookId(bookId);
    }

    @Override
    public Map<String, Object> read(Long id) {
        BookChapter chapter = bookChapterMapper.selectById(id);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }

        // 正文单独查，读一章才拉一章的内容
        BookContent bookContent = bookContentMapper.selectByChapterId(chapter.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("chapter", chapter);
        data.put("content", bookContent == null ? "" : bookContent.getContent());
        data.put("prevId", bookChapterMapper.selectPrevId(chapter.getBookId(), chapter.getChapterNum()));
        data.put("nextId", bookChapterMapper.selectNextId(chapter.getBookId(), chapter.getChapterNum()));
        return data;
    }

}
