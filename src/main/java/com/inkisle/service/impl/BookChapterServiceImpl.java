package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.service.BookChapterService;
import com.inkisle.service.BookService;
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

/**
 * 章节与正文
 * 正文体积较大，单独存放于 `book_content` 表，与章节一对一
 * 目录、列表只查章节表，可避免一次性取出大量正文数据
 */
@Service
public class BookChapterServiceImpl implements BookChapterService {

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookContentMapper bookContentMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private BookService bookService;

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
        // 章节接口对游客开放，作品须过审后方可阅读。仅在详情页拦截不足：
        // 直接以章节 id 请求正文，仍可读取被驳回作品的完整内容
        if (!bookService.isVisibleToReader(chapter.getBookId())) {
            throw new BusinessException("作品不存在或未上架");
        }
        return buildReadData(chapter);
    }

    @Override
    public Map<String, Object> readForAuthor(Long id, Long authorId) {
        BookChapter chapter = bookChapterMapper.selectById(id);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        // 作者预览的是本人稿件，未过审也需可读，故此处只校验归属，不校验审核状态
        checkBookOwner(chapter.getBookId(), authorId);
        return buildReadData(chapter);
    }

    /**
     * 组装阅读接口的返回。正文单独查询，每次只取一章；同时附带上一章、下一章的 id，
     * 供阅读页翻页按钮使用
     */
    private Map<String, Object> buildReadData(BookChapter chapter) {
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
            // 新章节追加在末尾，章节号取当前最大值加一
            Integer maxNum = bookChapterMapper.selectMaxChapterNum(chapter.getBookId());
            chapter.setChapterNum(maxNum == null ? 1 : maxNum + 1);
            chapter.setCreateTime(LocalDateTime.now());
            bookChapterMapper.insert(chapter);
        } else {
            BookChapter exist = bookChapterMapper.selectById(chapter.getId());
            if (exist == null) {
                throw new BusinessException("章节不存在");
            }
            // 章节号不可修改，否则会打乱目录顺序
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
            // 按 UTF-8 读取，该编码为常见文本编码
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
     * 正文与章节一对一，按 `chapter_id` 存在则更新，否则插入
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
     * 章节的增删改均经此校验：确认操作者即该作品的作者
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
