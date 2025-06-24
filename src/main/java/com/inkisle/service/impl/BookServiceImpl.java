package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.AuthorInfo;
import com.inkisle.entity.BookAuditRecord;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作品
 * 详情页读多写少，使用 @Cacheable 缓存；数据变更后需清除缓存，
 * 故写方法均标注 @CacheEvict；新建时 id 尚未生成，无法按 key 清除，故清空整片缓存
 */
@Service
public class BookServiceImpl implements BookService {

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
        int offset = (pageNum - 1) * pageSize;
        List<BookInfo> list = bookInfoMapper.selectPage(offset, pageSize, categoryId);

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
        bookInfo.setUpdateTime(LocalDateTime.now());
        bookInfoMapper.updateById(bookInfo);
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
        BookAuditRecord latest = bookAuditService.latestAudit(bookId);
        // 无任何审核记录时视为已上架，默认对读者可见
        if (latest == null) {
            return true;
        }
        Integer status = latest.getStatus();
        return status != null && status == 1;
    }

}
