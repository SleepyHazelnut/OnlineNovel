package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookChapterService;
import com.inkisle.util.TxtChapterParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookChapterServiceImpl implements BookChapterService {

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookContentMapper bookContentMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

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

    @Override
    public void saveChapter(BookChapter chapter, String content, Long authorId) {
        checkBookOwner(chapter.getBookId(), authorId);

        if (chapter.getId() == null) {
            // 新章节接在最后，章节号自己往后排
            Integer maxNum = bookChapterMapper.selectMaxChapterNum(chapter.getBookId());
            chapter.setChapterNum(maxNum == null ? 1 : maxNum + 1);
            chapter.setCreateTime(LocalDateTime.now());
            bookChapterMapper.insert(chapter);
        } else {
            BookChapter exist = bookChapterMapper.selectById(chapter.getId());
            if (exist == null) {
                throw new BusinessException("章节不存在");
            }
            // 章节号不许改，改了目录就乱了
            chapter.setChapterNum(exist.getChapterNum());
            bookChapterMapper.updateById(chapter);
        }

        saveContent(chapter.getId(), content);
    }

    @Override
    public void deleteChapter(Long chapterId, Long authorId) {
        BookChapter chapter = bookChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        checkBookOwner(chapter.getBookId(), authorId);

        bookChapterMapper.deleteById(chapterId);
        bookContentMapper.deleteByChapterId(chapterId);
    }

    @Override
    public int importChapters(Long bookId, MultipartFile file, Long authorId) {
        checkBookOwner(bookId, authorId);

        String text;
        try {
            // 先按 UTF-8 读，作者电脑上基本都是这个编码
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }

        Map<String, String> chapters = TxtChapterParser.split(text);
        if (chapters.isEmpty()) {
            throw new BusinessException("没解析出章节，检查一下标题格式");
        }

        Integer maxNum = bookChapterMapper.selectMaxChapterNum(bookId);
        int num = maxNum == null ? 0 : maxNum;

        for (Map.Entry<String, String> entry : chapters.entrySet()) {
            BookChapter chapter = new BookChapter();
            chapter.setBookId(bookId);
            chapter.setChapterName(entry.getKey());
            chapter.setChapterNum(++num);
            chapter.setCreateTime(LocalDateTime.now());
            bookChapterMapper.insert(chapter);

            saveContent(chapter.getId(), entry.getValue());
        }

        return chapters.size();
    }

    /**
     * 正文按 chapter_id 一对一，有就更新没有就插
     */
    private void saveContent(Long chapterId, String content) {
        if (content == null) {
            return;
        }
        BookContent exist = bookContentMapper.selectByChapterId(chapterId);
        if (exist == null) {
            BookContent bookContent = new BookContent();
            bookContent.setChapterId(chapterId);
            bookContent.setContent(content);
            bookContentMapper.insert(bookContent);
        } else {
            exist.setContent(content);
            bookContentMapper.updateByChapterId(exist);
        }
    }

    /**
     * 只有作品的作者能改它的章节
     */
    private void checkBookOwner(Long bookId, Long authorId) {
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(book.getAuthorId())) {
            throw new BusinessException("只能管理自己作品的章节");
        }
    }

}
