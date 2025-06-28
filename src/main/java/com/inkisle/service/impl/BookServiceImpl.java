package com.inkisle.service.impl;

import com.inkisle.common.BookAuditStatus;
import com.inkisle.common.BusinessException;
import com.inkisle.common.PageUtils;
import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.AuthorInfoMapper;
import com.inkisle.mapper.BookAuditRecordMapper;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookCommentMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.BookStatMapper;
import com.inkisle.mapper.UserBookshelfMapper;
import com.inkisle.mapper.UserReadHistoryMapper;
import com.inkisle.service.BookAuditService;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 作品
 * 详情页读多写少，使用 @Cacheable 缓存；数据变更后需清除缓存，
 * 故写方法均标注 @CacheEvict；新建时 id 尚未生成，无法按 key 清除，故清空整片缓存
 */
@Service
public class BookServiceImpl implements BookService {

    /** 未指定每页条数时的默认值 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 搜索关键词长度上限：再长也没有实际匹配意义，只会让 LIKE 更慢 */
    private static final int MAX_KEYWORD_LENGTH = 64;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private BookStatMapper bookStatMapper;

    @Autowired
    private AuthorInfoMapper authorInfoMapper;

    @Autowired
    private BookAuditService bookAuditService;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookContentMapper bookContentMapper;

    @Autowired
    private BookCommentMapper bookCommentMapper;

    @Autowired
    private BookAuditRecordMapper bookAuditRecordMapper;

    @Autowired
    private UserBookshelfMapper userBookshelfMapper;

    @Autowired
    private UserReadHistoryMapper userReadHistoryMapper;

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

    /**
     * 详情页读多写少，加入缓存
     */
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
        // 空关键词会退化成 LIKE '%%'，等于把整个书库当成搜索结果返回，页码也对不上，直接返回空
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return emptyPage();
        }
        if (kw.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException("搜索关键词最多 " + MAX_KEYWORD_LENGTH + " 个字");
        }

        int num = PageUtils.pageNum(pageNum);
        int size = PageUtils.pageSize(pageSize, DEFAULT_PAGE_SIZE);
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
     * 新增与修改共用同一方法：新建时 id 为空，无法按 key 清除缓存，故清空整片缓存
     */
    @Override
    @CacheEvict(value = "book", allEntries = true)
    public void save(BookSaveDTO bookSaveDTO, Long authorId) {
        // 资质未过审的作者不可发布作品，否则书城会出现未审核内容
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
        // 未填写作者名时使用笔名，避免列表显示为空
        bookInfo.setAuthorName(bookSaveDTO.getAuthorName() == null || bookSaveDTO.getAuthorName().isEmpty()
                ? author.getPenName() : bookSaveDTO.getAuthorName());

        if (bookInfo.getId() == null) {
            bookInfo.setAuthorId(authorId);
            // 新建作品默认为连载中
            bookInfo.setStatus(0);
            // 上架状态从「未提交」起步：读者端只认「已通过」，
            // 建完就能在书库看到等于审核流程形同虚设
            bookInfo.setAuditStatus(BookAuditStatus.DRAFT.getCode());
            bookInfo.setCreateTime(LocalDateTime.now());
            bookInfoMapper.insert(bookInfo);
            return;
        }

        BookInfo exist = bookInfoMapper.selectById(bookInfo.getId());
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        // 此处为手写校验，后续多个作者接口均需使用，宜抽取复用
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能修改自己的作品");
        }
        bookInfo.setAuthorId(authorId);
        // 编辑只改书名、简介这些展示信息。连载状态由 updateStatus 单独维护，
        // 这里必须带回归档值：updateById 会无条件写入 status，
        // 不带上就会把「已完结」覆盖成 null
        bookInfo.setStatus(exist.getStatus());
        bookInfo.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(bookInfo);

        // 已上架的作品改了展示信息，必须重新送审，否则改完简介就绕过了审核
        // 章节正文不在此列：若每次更新都要重审，作者每更一章书都得下架等审，功能没法用
        if (BookAuditStatus.isVisible(exist.getAuditStatus()) && displayChanged(exist, bookInfo)) {
            bookAuditService.resubmitAfterEdit(exist.getId(), authorId);
        }
    }

    /**
     * 只比较对外展示、审核员会看的字段
     * 空串与 null 视为相同：编辑页回填时空值可能变成 ""，不算改动，否则每次保存都会触发重审
     */
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

    /**
     * 删除作品
     * 章节、正文、评论、收藏、阅读记录、审核记录都以 book_id 关联本表，
     * 只删 book_info 会把这些数据留成读不到也清不掉的孤儿，故按依赖顺序逐表清理
     * 正文挂在章节上，必须先于章节删除，否则子查询已定位不到它
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "book", key = "#id")
    public void delete(Long id, Long authorId) {
        BookInfo exist = bookInfoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能删除自己的作品");
        }

        // 顺序不可调换：正文按章节 id 定位，章节一旦先删，正文就再也找不到
        bookContentMapper.deleteByBookId(id);
        bookChapterMapper.deleteByBookId(id);
        bookCommentMapper.deleteByBookId(id);
        userBookshelfMapper.deleteByBookId(id);
        userReadHistoryMapper.deleteByBookId(id);
        bookAuditRecordMapper.deleteByBookId(id);
        bookInfoMapper.deleteById(id);
    }

    @Override
    @CacheEvict(value = "book", key = "#id")
    public void updateStatus(Long id, Integer status, Long authorId) {
        BookInfo exist = bookInfoMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException("作品不存在");
        }
        if (!authorId.equals(exist.getAuthorId())) {
            throw new BusinessException("只能操作自己的作品");
        }
        // 0 连载，1 完结，其他值不接受
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值不对");
        }

        BookInfo update = new BookInfo();
        update.setId(id);
        update.setStatus(status);
        update.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateStatus(update);
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
        BookInfo book = bookInfoMapper.selectById(bookId);
        // 只认作品自身的上架状态，不再每次回查审核记录表
        return book != null && BookAuditStatus.isVisible(book.getAuditStatus());
    }

}
