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
        // 章节接口对游客开放，作品须过审后方可阅读。仅在详情页拦截不足：
        // 直接以章节 id 请求正文，仍可读取被驳回作品的完整内容
        if (!bookService.isVisibleToReader(chapter.getBookId())) {
            throw new BusinessException("作品不存在或未上架");
        }
        // 目录已过滤未过审章节，这里再拦一次：知道章节 id 就能直接请求正文
        if (!ContentAuditStatus.isVisible(chapter.getAuditStatus())) {
            throw new BusinessException("这一章还没通过审核");
        }
        // 阅读次数在读者打开正文时累加，作者预览走下面的 readForAuthor，不计入
        bookInfoMapper.incrementVisitCount(chapter.getBookId());
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

    /**
     * 章节与正文分两张表存放，正文写失败时章节不该留下，
     * 否则目录里会出现点开一片空白的章节
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChapter(BookChapter chapter, String content, Long authorId) {
        if (chapter.getId() == null) {
            // 新增：bookId 由请求给出，先确认这本书属于当前作者
            checkBookOwner(chapter.getBookId(), authorId);
            // 锁住作品行再取最大章节号：不加锁时两个并发追加会读到同一个最大值，
            // 写出两个相同的章节号，目录顺序就乱了
            bookInfoMapper.selectIdForUpdate(chapter.getBookId());
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
            // 归属以库中记录为准，不能拿请求体里的 bookId 校验：
            // 否则用自己的 bookId 就能过校验，再按 id 改掉别人书里的章节
            checkBookOwner(exist.getBookId(), authorId);
            chapter.setBookId(exist.getBookId());
            // 章节号不可修改，否则会打乱目录顺序
            chapter.setChapterNum(exist.getChapterNum());
            bookChapterMapper.updateById(chapter);
        }

        saveContent(chapter.getId(), content);
        refreshBookStat(chapter.getBookId());
    }

    /**
     * 章节与其正文一并删除，任一步失败都整体回退
     */
    @Override
    public List<BookChapter> listByBookIdForAuthor(Long bookId, Long authorId) {
        checkBookOwner(bookId, authorId);
        // 作者端要看到待审与驳回的章节，才能知道哪些还没放出去
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
        // 阅读进度指向的章节被删后，读者再从书架续读会跳到一个不存在的章节
        userReadHistoryMapper.deleteByChapterId(chapterId);
        refreshBookStat(chapter.getBookId());
    }

    /**
     * 从 txt 导入章节。整本书是一个整体，解析或写库中途失败时
     * 不应给作者留下半本，故全部章节放在同一个事务里
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
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

        // 与单章追加同一处理：先锁作品行，避免与另一个导入或追加请求抢同一个起始号
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
        // 第一章的正文写进来才知道字数，故放在写正文之后
        bookChapterMapper.updateWordCount(chapterId, content.length());
    }

    /**
     * 章节增删改后统一收口：重算作品总字数，并把作品的更新时间推到当下
     * 更新榜与首页「最新更新」都按 update_time 排序，不刷新的话作者更了章，
     * 这两个位置还停在作品信息最后一次修改的时间，看起来像没更新过
     */
    private void refreshBookStat(Long bookId) {
        bookInfoMapper.updateWordCount(bookId, bookChapterMapper.selectTotalWordCount(bookId));
        bookInfoMapper.touchUpdateTime(bookId, LocalDateTime.now());
        // 详情缓存里存着整个 BookInfo，不清的话字数与「共几章」会停在旧值
        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(bookId);
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
