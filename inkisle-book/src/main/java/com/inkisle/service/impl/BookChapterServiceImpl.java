package com.inkisle.service.impl;

import com.inkisle.cache.CacheAside;
import com.inkisle.cache.CacheInvalidator;
import com.inkisle.cache.CacheNames;
import com.inkisle.cache.VisitCounter;
import com.inkisle.common.BusinessException;
import com.inkisle.common.ContentAuditStatus;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.entity.BookInfo;
import com.inkisle.entity.ChapterNeighbors;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.UserReadHistoryMapper;
import com.inkisle.service.BookChapterService;
import com.inkisle.service.BookService;
import com.inkisle.util.ChapterSlice;
import com.inkisle.util.TxtChapterParser;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 章节和正文分表，正文在 book_content，查目录不碰正文
 */
@Service
public class BookChapterServiceImpl implements BookChapterService {

    private static final Logger log = LoggerFactory.getLogger(BookChapterServiceImpl.class);

    /** 整本导入时一批写多少章：太小往返多，太大单条 SQL 会超 max_allowed_packet */
    private static final int IMPORT_BATCH_SIZE = 200;

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
    private CacheAside cacheAside;

    @Autowired
    private CacheInvalidator cacheInvalidator;

    @Autowired
    private VisitCounter visitCounter;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate batchTx;

    @PostConstruct
    void initBatchTransaction() {
        batchTx = new TransactionTemplate(transactionManager);
        // 用 REQUIRED 而不是 REQUIRES_NEW：调用方没有事务时它就是每批独立提交，
        // 后面的批次失败不会把前面已经写进去的几百章一起回滚，一本几千章的书
        // 不用因为最后一章出错就从头再来；调用方本来就带着事务时（比如单测），
        // 它跟着外层走——REQUIRES_NEW 会另开一个连接去抢外层已经持有的作品行锁，
        // 自己等自己，只能等到 innodb_lock_wait_timeout
        batchTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    @Override
    public List<BookChapter> listByBookId(Long bookId) {
        // 目录只随章节增删和审核状态变，读远多于写。空列表照样缓存：
        // 一个没有章节的书被反复请求，不该每次都落到库上
        return cacheAside.getOrLoad(CacheNames.CHAPTER_CATALOG, CacheNames.catalogKey(bookId),
                () -> bookChapterMapper.selectListByBookId(bookId));
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
        // 计数只写 Redis，读接口不再有写操作；回写交给定时任务
        visitCounter.record(chapter.getBookId());
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
        // 正文是这条链路上最大的一条数据，且只有作者改稿才会变。
        // 正文缺失时缓存空串而不是不缓存：这条记录不存在，反复回源也查不出东西，
        // 写入正文的那条路径会把 key 删掉，所以不会出现「刚传上去读不到」
        String content = cacheAside.getOrLoad(CacheNames.CHAPTER_CONTENT,
                CacheNames.contentKey(chapter.getId()), () -> {
                    BookContent row = bookContentMapper.selectByChapterId(chapter.getId());
                    return row == null ? "" : row.getContent();
                });

        // 上下章一次查出，不分成两次往返
        ChapterNeighbors neighbors = bookChapterMapper.selectPrevNext(
                chapter.getBookId(), chapter.getChapterNum());

        Map<String, Object> data = new HashMap<>();
        data.put("chapter", chapter);
        data.put("content", content);
        data.put("prevId", neighbors == null ? null : neighbors.getPrevId());
        data.put("nextId", neighbors == null ? null : neighbors.getNextId());
        return data;
    }

    /** 章节与正文分两张表写，正文失败时章节不能留下 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChapter(BookChapter chapter, String content, Long authorId) {
        // 改稿要按新旧字数差更新总字数，差值从库里的旧记录取，不能拿请求体里的
        int oldWordCount = 0;
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
            oldWordCount = exist.getWordCount() == null ? 0 : exist.getWordCount();
            bookChapterMapper.updateById(chapter);
        }

        saveContent(chapter.getId(), content);
        // 正文没传（只改章节名）时字数不变，差值为零
        int newWordCount = content == null ? oldWordCount : content.length();
        refreshBookStat(chapter.getBookId(), newWordCount - oldWordCount, true);
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
        // 正文缓存按章节 id 存，章节删了缓存得跟着走，否则回收站里还留着一份可读的正文
        cacheInvalidator.evict(CacheNames.CHAPTER_CONTENT, CacheNames.contentKey(chapterId));
        // 阅读进度指向的章节已删除，续读会跳到不存在的页
        userReadHistoryMapper.deleteByChapterId(chapterId);
        // 删章只改总字数：删除一章不算更新，更新更新时间会把它排到更新榜前面
        int removedWords = chapter.getWordCount() == null ? 0 : chapter.getWordCount();
        refreshBookStat(chapter.getBookId(), -removedWords, false);
    }

    /**
     * 整本导入
     *
     * 早先整个方法挂一个事务：读文件、正则切分、几百批 INSERT、最后重算字数，
     * 五千章的书就是一个持续几十秒的大事务，作品的行驶锁从取号一直持到提交，
     * 期间同书任何写操作都得排队，失败还要把写了一半的几千行全部回滚。
     * 现在只有「取号」和「每批写入」进事务，且每批独立提交。
     */
    @Override
    public int importChapters(Long bookId, MultipartFile file, Long authorId) {
        checkBookOwner(bookId, authorId);

        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("文件读取失败");
        }

        List<ChapterSlice> slices = TxtChapterParser.split(text);
        if (slices.isEmpty()) {
            throw new BusinessException("没解析出章节，检查一下标题格式");
        }

        // 取号要短：锁作品行只为拿到起始章节号，拿到就放，后面按批写不再持锁
        Integer maxNum = batchTx.execute(status -> {
            bookInfoMapper.selectIdForUpdate(bookId);
            return bookChapterMapper.selectMaxChapterNum(bookId);
        });
        int firstNum = maxNum == null ? 1 : maxNum + 1;

        int done = 0;
        for (int from = 0; from < slices.size(); from += IMPORT_BATCH_SIZE) {
            int to = Math.min(from + IMPORT_BATCH_SIZE, slices.size());
            int offset = from;
            try {
                batchTx.executeWithoutResult(status ->
                        insertSlices(bookId, slices.subList(offset, to), firstNum + offset));
            } catch (RuntimeException e) {
                // 已经提交的批次留着：它们本身就是完整的一批章节，重导只会往后追加
                log.error("整本导入中断 bookId={} 已入库 {} 章", bookId, done, e);
                throw new BusinessException("导入到第 " + (from + 1) + " 章时失败，前 " + done + " 章已入库");
            }
            done = to;
        }

        refreshBookStat(bookId, totalWordCount(slices), true);
        return slices.size();
    }

    /** 一批之内的章节与正文一起写，正文按回填出来的章节 id 关联 */
    private void insertSlices(Long bookId, List<ChapterSlice> batch, int firstNum) {
        LocalDateTime now = LocalDateTime.now();
        List<BookChapter> rows = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            ChapterSlice slice = batch.get(i);
            BookChapter chapter = new BookChapter();
            chapter.setBookId(bookId);
            chapter.setChapterName(slice.title());
            chapter.setChapterNum(firstNum + i);
            // 字数在这里就算好，省掉插入后再逐章 update 一次
            chapter.setWordCount(slice.content().length());
            chapter.setCreateTime(now);
            rows.add(chapter);
        }
        // 批量插入会把自增主键回填到对象上，正文再按这些 id 写
        bookChapterMapper.insertBatch(rows);

        List<BookContent> contentRows = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            BookContent bookContent = new BookContent();
            bookContent.setChapterId(rows.get(i).getId());
            bookContent.setContent(batch.get(i).content());
            contentRows.add(bookContent);
        }
        bookContentMapper.insertBatch(contentRows);
    }

    private int totalWordCount(List<ChapterSlice> slices) {
        int total = 0;
        for (ChapterSlice slice : slices) {
            total += slice.content().length();
        }
        return total;
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
        // 事务提交后才删，否则删完到提交之间进来的读请求会把旧正文写回缓存
        cacheInvalidator.evict(CacheNames.CHAPTER_CONTENT, CacheNames.contentKey(chapterId));
    }

    /**
     * 章节一变动就更新作品统计。
     * 总字数按增量加减，不再每次 SUM 全书章节：三千章的书存一章就要扫三千行。
     * 全量重算的 SQL 留着做对账，不放写路径上。
     * 新增和修改还要推一下更新时间，首页的最新更新和更新榜都按这个排，
     * 详情缓存里存的是整个 BookInfo，不清会一直是旧值。
     * 目录也要清：增删章节、审核状态变化都会让读者看到的目录变样。
     */
    private void refreshBookStat(Long bookId, int wordDelta, boolean touchUpdateTime) {
        if (wordDelta != 0) {
            bookInfoMapper.addWordCount(bookId, wordDelta);
        }
        if (touchUpdateTime) {
            bookInfoMapper.touchUpdateTime(bookId, LocalDateTime.now());
        }
        cacheInvalidator.evict(CacheNames.BOOK, bookId);
        cacheInvalidator.evict(CacheNames.CHAPTER_CATALOG, CacheNames.catalogKey(bookId));
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
