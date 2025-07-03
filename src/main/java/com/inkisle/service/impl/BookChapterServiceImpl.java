package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.common.ContentAuditStatus;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.UserReadHistoryMapper;
import com.inkisle.service.BookChapterService;
import com.inkisle.service.BookService;
import com.inkisle.util.TxtChapterParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 章节与正文分表存，正文在 book_content，目录查询不碰正文
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
    private UserReadHistoryMapper userReadHistoryMapper;

    @Autowired
    private BookService bookService;

    @Autowired
    private CacheManager cacheManager;

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
        // 章节接口对游客开放，光在详情页拦不够，直接按 id 请求正文一样能读到
        if (!bookService.isVisibleToReader(chapter.getBookId())) {
            throw new BusinessException("作品不存在或未上架");
        }
        // 目录已经过滤过，这里再拦一次，防止拿 id 直接读
        if (!ContentAuditStatus.isVisible(chapter.getAuditStatus())) {
            throw new BusinessException("这一章还没通过审核");
        }
        bookInfoMapper.incrementVisitCount(chapter.getBookId());
        return buildReadData(chapter);
    }

    @Override
    public Map<String, Object> readForAuthor(Long id, Long authorId) {
        BookChapter chapter = bookChapterMapper.selectById(id);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        // 作者看的是自己的稿子，未过审也要能打开，所以只校验归属
        checkBookOwner(chapter.getBookId(), authorId);
        return buildReadData(chapter);
    }

    private Map<String, Object> buildReadData(BookChapter chapter) {
        BookContent bookContent = bookContentMapper.selectByChapterId(chapter.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("chapter", chapter);
        data.put("content", bookContent == null ? "" : bookContent.getContent());
        data.put("prevId", bookChapterMapper.selectPrevId(chapter.getBookId(), chapter.getChapterNum()));
        data.put("nextId", bookChapterMapper.selectNextId(chapter.getBookId(), chapter.getChapterNum()));
        return data;
    }

    /** 章节与正文分两张表写，正文失败时章节不能留下 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChapter(BookChapter chapter, String content, Long authorId) {
        if (chapter.getId() == null) {
            checkBookOwner(chapter.getBookId(), authorId);
            // 先锁作品行再取号，两个并发追加才不会拿到同一个章节号
            bookInfoMapper.selectIdForUpdate(chapter.getBookId());
            Integer maxNum = bookChapterMapper.selectMaxChapterNum(chapter.getBookId());
            chapter.setChapterNum(maxNum == null ? 1 : maxNum + 1);
            chapter.setCreateTime(LocalDateTime.now());
            bookChapterMapper.insert(chapter);
        } else {
            BookChapter exist = bookChapterMapper.selectById(chapter.getId());
            if (exist == null) {
                throw new BusinessException("章节不存在");
            }
            // 归属以库里的记录为准：拿请求体的 bookId 校验会被绕过，改到别人的章节上
            checkBookOwner(exist.getBookId(), authorId);
            chapter.setBookId(exist.getBookId());
            chapter.setChapterNum(exist.getChapterNum());
            bookChapterMapper.updateById(chapter);
        }

        saveContent(chapter.getId(), content);
        refreshBookStat(chapter.getBookId());
    }

    @Override
    public List<BookChapter> listByBookIdForAuthor(Long bookId, Long authorId) {
        checkBookOwner(bookId, authorId);
        return bookChapterMapper.selectAllByBookId(bookId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChapter(Long chapterId, Long authorId) {
        BookChapter chapter = bookChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        checkBookOwner(chapter.getBookId(), authorId);

        bookChapterMapper.deleteById(chapterId);
        bookContentMapper.deleteByChapterId(chapterId);
        // 阅读进度指向的章节没了，读者续读会跳到不存在的页
        userReadHistoryMapper.deleteByChapterId(chapterId);
        refreshBookStat(chapter.getBookId());
    }

    /** 整本导入放一个事务里，失败不留半本书 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importChapters(Long bookId, MultipartFile file, Long authorId) {
        checkBookOwner(bookId, authorId);

        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }

        Map<String, String> chapters = TxtChapterParser.split(text);
        if (chapters.isEmpty()) {
            throw new BusinessException("没解析出章节，检查一下标题格式");
        }

        // 与单章追加一样先锁作品行，免得和另一个导入抢同一个起始号
        bookInfoMapper.selectIdForUpdate(bookId);
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

        refreshBookStat(bookId);
        return chapters.size();
    }

    /** 正文与章节一对一，有则更新，没有则插入 */
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
        // 字数按正文字符数算，换行和标点也计入
        bookChapterMapper.updateWordCount(chapterId, content.length());
    }

    /**
     * 章节变动后重算总字数，并推一下更新时间：首页「最新更新」和更新榜都按它排序
     * 详情缓存里存着整个 BookInfo，不清会停在旧值
     */
    private void refreshBookStat(Long bookId) {
        bookInfoMapper.updateWordCount(bookId, bookChapterMapper.selectTotalWordCount(bookId));
        bookInfoMapper.touchUpdateTime(bookId, LocalDateTime.now());
        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(bookId);
        }
    }

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
