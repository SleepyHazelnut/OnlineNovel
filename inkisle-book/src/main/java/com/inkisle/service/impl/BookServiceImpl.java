package com.inkisle.service.impl;

import com.inkisle.cache.CacheInvalidator;
import com.inkisle.cache.CacheNames;
import com.inkisle.client.AuditClient;
import com.inkisle.common.BookAuditStatus;
import com.inkisle.common.BusinessException;
import com.inkisle.common.PageUtils;
import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.AuthorInfoMapper;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookCommentMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.BookRatingMapper;
import com.inkisle.mapper.BookStatMapper;
import com.inkisle.mapper.HomeBookMapper;
import com.inkisle.mapper.UserBookshelfMapper;
import com.inkisle.mapper.UserReadHistoryMapper;
import com.inkisle.search.BookSearchEngine;
import com.inkisle.search.BookSearchIndexer;
import com.inkisle.service.BookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 作品。详情读多写少，加了缓存，写方法统一清缓存
 */
@Slf4j
@Service
public class BookServiceImpl implements BookService {

    /** 未指定每页条数时的默认值 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 搜索关键词长度上限，再长只会拖慢 LIKE */
    private static final int MAX_KEYWORD_LENGTH = 64;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private BookStatMapper bookStatMapper;

    @Autowired
    private AuthorInfoMapper authorInfoMapper;

    @Autowired
    private AuditClient auditClient;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookContentMapper bookContentMapper;

    @Autowired
    private BookCommentMapper bookCommentMapper;

    @Autowired
    private BookRatingMapper bookRatingMapper;

    @Autowired
    private HomeBookMapper homeBookMapper;

    @Autowired
    private UserBookshelfMapper userBookshelfMapper;

    @Autowired
    private UserReadHistoryMapper userReadHistoryMapper;

    @Autowired
    private BookSearchIndexer bookSearchIndexer;

    @Autowired
    private BookSearchEngine bookSearchEngine;

    @Autowired
    private CacheInvalidator cacheInvalidator;

    @Override
    public Map<String, Object> page(Integer pageNum, Integer pageSize, Long categoryId) {
        int num = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, DEFAULT_PAGE_SIZE);
        List<BookInfo> list = bookInfoMapper.selectPage(PageUtils.offset(num, size), size, categoryId);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", bookInfoMapper.countByCategory(categoryId));
        return data;
    }

    @Override
    @Cacheable(value = "book", key = "#id")
    public BookInfo getById(Long id) {
        return bookInfoMapper.selectById(id);
    }

    @Override
    public BookInfo getForAuthor(Long bookId, Long authorId) {
        BookInfo book = bookInfoMapper.selectById(bookId);
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(book.getAuthorId())) {
            throw new BusinessException("只能看自己的作品");
        }
        return book;
    }

    @Override
    public Map<String, Object> search(Integer pageNum, Integer pageSize, String keyword) {
        // 空关键词会变成 LIKE '%%'，等于查全表
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return emptyPage();
        }
        if (kw.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException("搜索关键词最多 " + MAX_KEYWORD_LENGTH + " 个字");
        }

        int num = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, DEFAULT_PAGE_SIZE);

        // 先问 ES，它能按字段权重打分，同分还能拿阅读数兜一下，MySQL 全文索引给不了
        // 返回 null 表示 ES 未启用或不可用，退回原有查询
        Map<String, Object> indexed = bookSearchEngine.search(kw, num, size);
        if (indexed != null) {
            return indexed;
        }

        List<BookInfo> list = bookInfoMapper.searchByKeyword(PageUtils.offset(num, size), size, kw);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", bookInfoMapper.countByKeyword(kw));
        return data;
    }

    private Map<String, Object> emptyPage() {
        Map<String, Object> data = new HashMap<>();
        data.put("list", Collections.emptyList());
        data.put("total", 0);
        return data;
    }

    @Override
    public List<BookInfo> listByAuthor(Long authorId) {
        return bookInfoMapper.selectByAuthorId(authorId);
    }

    /**
     * 新建和修改都走这里
     * 不再整片清缓存：改一本书就清掉全部作品，等于把别人正在读的详情也一起打回库中，
     * 流量越大越明显。insert 之后能拿到 id，改完精确清这一条即可
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(BookSaveDTO bookSaveDTO, Long authorId) {
        // 作者资质没过审不能发书
        // 作者表归用户服务，这里共库直接读，改资质仍走用户服务
        AuthorInfo author = authorInfoMapper.selectById(authorId);
        if (author == null || author.getAuditStatus() == null || author.getAuditStatus() != 1) {
            throw new BusinessException("作者资质还没通过审核，暂时不能发布作品");
        }

        BookInfo bookInfo = new BookInfo();
        bookInfo.setId(bookSaveDTO.getId());
        bookInfo.setBookName(bookSaveDTO.getBookName());
        bookInfo.setCategoryId(bookSaveDTO.getCategoryId());
        bookInfo.setBookDesc(bookSaveDTO.getBookDesc());
        bookInfo.setCoverUrl(bookSaveDTO.getCoverUrl());
        bookInfo.setAuthorName(bookSaveDTO.getAuthorName() == null || bookSaveDTO.getAuthorName().isEmpty()
                ? author.getPenName() : bookSaveDTO.getAuthorName());

        if (bookInfo.getId() == null) {
            bookInfo.setAuthorId(authorId);
            bookInfo.setStatus(0);
            // 建完就上架等于审核没用，所以从「未提交」起步
            bookInfo.setAuditStatus(BookAuditStatus.DRAFT.getCode());
            bookInfo.setCreateTime(LocalDateTime.now());
            bookInfoMapper.insert(bookInfo);
            evictBook(bookInfo.getId());
            // 新建的书是「未提交」，这一步实际只是把它从索引中移除
            // 保留是为了写完即同步，避免其他调用点遗漏
            bookSearchIndexer.sync(bookInfo.getId());
            return;
        }

        BookInfo exist = bookInfoMapper.selectById(bookInfo.getId());
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能修改自己的作品");
        }
        bookInfo.setAuthorId(authorId);
        // updateById 会无条件写 status，编辑时需要把原值带回，否则「已完结」会被置空
        bookInfo.setStatus(exist.getStatus());
        bookInfo.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(bookInfo);
        evictBook(bookInfo.getId());
        // 改名、换封面会影响搜索结果，改了就要重新灌一次索引
        bookSearchIndexer.sync(bookInfo.getId());

        // 改书名、简介这类展示信息要重审，改章节正文不用，否则每更新一章都要等待审核
        if (BookAuditStatus.isVisible(exist.getAuditStatus()) && displayChanged(exist, bookInfo)) {
            resubmitAfterCommit(exist.getId(), authorId);
        }
    }

    /**
     * 送审必须放在事务提交之后
     *
     * 这里持有 book_info 的行锁，如果同步发这次远程调用，审核服务回头又要通过
     * /internal/book/{id}/audit-status 改同一行，那一步会等本事务提交；
     * 而本事务在等这次 HTTP 响应 —— 两个请求互等，MySQL 检测不到死锁，
     * 只能干等到 innodb_lock_wait_timeout（默认 50 秒）报错。编辑作品必现。
     * 提交后再发，行锁已经释放，远程调用慢也只是慢这一下，不占着数据库连接
     */
    private void resubmitAfterCommit(Long bookId, Long authorId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            doResubmit(bookId, authorId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                doResubmit(bookId, authorId);
            }
        });
    }

    /** 提交之后本地已经改完，送审失败不该让作者看到保存失败，记下来由作者重试或管理端补偿 */
    private void doResubmit(Long bookId, Long authorId) {
        try {
            auditClient.resubmitAfterEdit(bookId, authorId);
        } catch (Exception e) {
            log.warn("编辑后送审失败，作品状态可能仍是已上架 bookId={}", bookId, e);
        }
    }

    /** 空串和 null 算同一个值，否则编辑页每次保存都会触发重审 */
    private boolean displayChanged(BookInfo before, BookInfo after) {
        return textChanged(before.getBookName(), after.getBookName())
                || textChanged(before.getAuthorName(), after.getAuthorName())
                || textChanged(before.getBookDesc(), after.getBookDesc())
                || textChanged(before.getCoverUrl(), after.getCoverUrl())
                || !Objects.equals(before.getCategoryId(), after.getCategoryId());
    }

    private boolean textChanged(String before, String after) {
        return !Objects.equals(before == null ? "" : before.trim(), after == null ? "" : after.trim());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long authorId) {
        BookInfo exist = bookInfoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能删除自己的作品");
        }
        deleteCascade(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long id) {
        if (bookInfoMapper.selectById(id) == null) {
            throw new BusinessException("作品不存在");
        }
        deleteCascade(id);
    }

    /** 作者删书和管理员删书共用这一段，只是入口处校验不同 */
    private void deleteCascade(Long id) {
        // 顺序不能换：正文按章节 id 定位，章节先删就找不到了
        bookContentMapper.deleteByBookId(id);
        bookChapterMapper.deleteByBookId(id);
        bookCommentMapper.deleteByBookId(id);
        bookRatingMapper.deleteByBookId(id);
        homeBookMapper.deleteByBookId(id);
        userBookshelfMapper.deleteByBookId(id);
        userReadHistoryMapper.deleteByBookId(id);
        // 审核记录在审核服务里。这一步放到事务提交之后：远程调用一旦慢下来，
        // 整个删除事务就会一直攥着数据库连接，本地这几条 DELETE 早就写完了还在等它
        removeAuditRecordsAfterCommit(id);
        bookInfoMapper.deleteById(id);
        // 书已经查不到了，同步的结果是从索引里删掉这条
        bookSearchIndexer.sync(id);
        // 详情和目录一起清。正文按章节 id 存，这里拿不到 id 列表，
        // 但 read() 会先校验作品可见性，删掉之后这些 key 已经读不到，等 TTL 自然回收
        evictBook(id);
        cacheInvalidator.evict(CacheNames.CHAPTER_CATALOG, CacheNames.catalogKey(id));
    }

    /**
     * 审核记录由审核服务管，本地事务提交成功才发这次远程删除
     * 提交前发的话，本地回滚了审核记录已经没了，成了不可恢复的脏数据
     */
    private void removeAuditRecordsAfterCommit(Long bookId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            removeAuditRecords(bookId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                removeAuditRecords(bookId);
            }
        });
    }

    /** 提交之后已经无法回滚，失败只能记下来由管理端补偿，不能让异常冒出去 */
    private void removeAuditRecords(Long bookId) {
        try {
            auditClient.deleteByBookId(bookId);
        } catch (Exception e) {
            log.warn("删书后清理审核记录失败，需人工补偿 bookId={}", bookId, e);
        }
    }

    /** 清掉一本书的详情缓存，key 与 getById 上的 @Cacheable 保持一致。事务内调用会推迟到提交后 */
    private void evictBook(Long bookId) {
        if (bookId == null) {
            return;
        }
        cacheInvalidator.evict(CacheNames.BOOK, bookId);
    }

    @Override
    public void updateStatus(Long id, Integer status, Long authorId) {
        BookInfo exist = bookInfoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能操作自己的作品");
        }
        // 0 连载，1 完结
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不对");
        }

        BookInfo update = new BookInfo();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateStatus(update);
        evictBook(id);
        bookSearchIndexer.sync(id);
    }

    @Override
    public Map<String, Object> stat(Long bookId, Long authorId) {
        BookInfo exist = bookInfoMapper.selectById(bookId);
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能看自己作品的数据");
        }
        return bookStatMapper.selectBookStat(bookId);
    }

    @Override
    public boolean isVisibleToReader(Long bookId) {
        if (bookId == null) {
            return false;
        }
        // 读详情缓存：getById 上挂着 @Cacheable，但同类里自调用不过代理，
        // 这里直接读缓存区域，key 与 @Cacheable 生成的一致，两边共用一份
        Cache cache = cacheManager.getCache(CacheNames.BOOK);
        BookInfo book = cache == null ? null : cache.get(bookId, BookInfo.class);
        if (book == null) {
            book = bookInfoMapper.selectById(bookId);
            // 只写回可见的书：未上架的状态一旦缓存，审核通过后要等 TTL 过期读者才看得到
            if (book != null && cache != null && BookAuditStatus.isVisible(book.getAuditStatus())) {
                cache.put(bookId, book);
            }
        }
        return book != null && BookAuditStatus.isVisible(book.getAuditStatus());
    }

}
