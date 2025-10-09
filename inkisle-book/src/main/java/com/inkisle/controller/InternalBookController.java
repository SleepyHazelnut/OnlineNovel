package com.inkisle.controller;

import com.inkisle.cache.CacheAside;
import com.inkisle.cache.CacheNames;
import com.inkisle.common.BusinessException;
import com.inkisle.dto.AuditStatusDTO;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookContent;
import com.inkisle.entity.BookInfo;
import com.inkisle.mapper.BookChapterMapper;
import com.inkisle.mapper.BookContentMapper;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.search.BookSearchIndexer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 给审核服务用的内部接口
 * 网关不转发 /internal，外面访问不到。拦截器里也放行，服务之间调不带用户 token
 */
@RestController
@RequestMapping("/internal")
public class InternalBookController {

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private BookChapterMapper bookChapterMapper;

    @Autowired
    private BookContentMapper bookContentMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CacheAside cacheAside;

    @Autowired
    private BookSearchIndexer bookSearchIndexer;

    @GetMapping("/book/{id}")
    public BookInfo getBook(@PathVariable Long id) {
        return bookInfoMapper.selectById(id);
    }

    @GetMapping("/book/list")
    public Map<String, Object> listByAuditStatus(@RequestParam Integer status,
                                                 @RequestParam int offset,
                                                 @RequestParam int size) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", bookInfoMapper.selectByAuditStatus(status, offset, size));
        data.put("total", bookInfoMapper.countByAuditStatus(status));
        return data;
    }

    /**
     * 置为待审，返回影响行数。状态已经是待审时返回 0，审核服务据此判断重复提交
     */
    @PostMapping("/book/{id}/pending")
    public Integer markPending(@PathVariable Long id) {
        int rows = bookInfoMapper.markPendingIfNotPending(id, LocalDateTime.now());
        evictBookCache(id);
        // 待审即读者不可见，索引里这条要去掉
        bookSearchIndexer.sync(id);
        return rows;
    }

    /** 通过、驳回、下架、恢复上架都走这里，remark 传 null 表示清空 */
    @PostMapping("/book/{id}/audit-status")
    public void updateBookStatus(@PathVariable Long id, @RequestBody AuditStatusDTO dto) {
        bookInfoMapper.updateAuditStatus(id, dto.getStatus(), dto.getRemark(), LocalDateTime.now());
        // 详情缓存里存着整个 BookInfo，不清会读到旧状态
        evictBookCache(id);
        // 上架的书进索引、下架的书出索引，改一次状态同步一次
        bookSearchIndexer.sync(id);
    }

    @GetMapping("/book/{id}/chapters")
    public List<BookChapter> listChapters(@PathVariable Long id) {
        return bookChapterMapper.selectAllByBookId(id);
    }

    /** 作品通过审核后，已有章节不再逐条审，一次性放行 */
    @PostMapping("/book/{id}/chapters/pass-all")
    public void passAllChapters(@PathVariable Long id) {
        bookChapterMapper.passAllByBookId(id);
        // 一次放行整本，读者目录会多出全部章节，缓存里的那份立刻过期
        cacheAside.evict(CacheNames.CHAPTER_CATALOG, CacheNames.catalogKey(id));
    }

    @GetMapping("/chapter/{id}")
    public BookChapter getChapter(@PathVariable Long id) {
        return bookChapterMapper.selectById(id);
    }

    @GetMapping("/chapter/list")
    public Map<String, Object> listChaptersByStatus(@RequestParam Integer status,
                                                    @RequestParam int offset,
                                                    @RequestParam int size) {
        Map<String, Object> data = new HashMap<>();
        data.put("list", bookChapterMapper.selectPageByStatus(status, offset, size));
        data.put("total", bookChapterMapper.countByStatus(status));
        return data;
    }

    @PostMapping("/chapter/{id}/audit-status")
    public void updateChapterStatus(@PathVariable Long id, @RequestBody AuditStatusDTO dto) {
        BookChapter chapter = bookChapterMapper.selectById(id);
        if (chapter == null) {
            throw new BusinessException("章节不存在");
        }
        bookChapterMapper.updateAuditStatus(id, dto.getStatus(), dto.getRemark());
        // 章节过审后读者端目录多一条、驳回后少一条，两种都要清
        cacheAside.evict(CacheNames.CHAPTER_CATALOG, CacheNames.catalogKey(chapter.getBookId()));
        // 章节驳回后读者端目录会少一条，详情里的章节统计要跟着变
        evictBookCache(chapter.getBookId());
    }

    @GetMapping("/chapter/{id}/content")
    public Map<String, Object> getChapterContent(@PathVariable Long id) {
        BookContent content = bookContentMapper.selectByChapterId(id);
        Map<String, Object> data = new HashMap<>();
        data.put("content", content == null ? "" : content.getContent());
        return data;
    }

    private void evictBookCache(Long bookId) {
        Cache cache = cacheManager.getCache("book");
        if (cache != null) {
            cache.evict(bookId);
        }
    }

}
