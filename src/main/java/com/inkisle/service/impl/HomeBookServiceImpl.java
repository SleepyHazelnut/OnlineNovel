package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.dto.HomeBookDTO;
import com.inkisle.entity.BookInfo;
import com.inkisle.entity.HomeBook;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.HomeBookMapper;
import com.inkisle.service.HomeBookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页推荐位
 */
@Service
public class HomeBookServiceImpl implements HomeBookService {

    /** 编号与 book_info.audit_status 的「已通过」对应，改动时两处需同步 */
    private static final int AUDIT_PASSED = 2;

    /** 最新更新板块的条数，与前端一屏放得下的数量一致 */
    private static final int NEWEST_SIZE = 6;

    @Autowired
    private HomeBookMapper homeBookMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Override
    public List<Map<String, Object>> listBySection(Integer section) {
        return homeBookMapper.selectBySection(checkSection(section));
    }

    @Override
    public Map<String, Object> recommend() {
        Map<String, Object> data = new HashMap<>();
        data.put("hot", homeBookMapper.selectVisibleBySection(1));
        data.put("quality", homeBookMapper.selectVisibleBySection(2));
        data.put("newest", newest());
        return data;
    }

    @Override
    public void add(HomeBookDTO dto) {
        BookInfo book = bookInfoMapper.selectById(dto.getBookId());
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        // 未上架的书放进推荐位，读者端也要被过滤掉，等于配了个看不见的条目
        if (book.getAuditStatus() == null || book.getAuditStatus() != AUDIT_PASSED) {
            throw new BusinessException("只有已上架的作品可以上推荐位");
        }
        if (homeBookMapper.countBySectionAndBook(dto.getSection(), dto.getBookId()) > 0) {
            throw new BusinessException("这本书已在该板块");
        }

        HomeBook homeBook = new HomeBook();
        homeBook.setBookId(dto.getBookId());
        homeBook.setSection(dto.getSection());
        homeBook.setSort(dto.getSort() == null ? 0 : dto.getSort());
        homeBook.setCreateTime(LocalDateTime.now());
        homeBookMapper.insert(homeBook);
    }

    @Override
    public void updateSort(Long id, Integer sort) {
        if (id == null) {
            throw new BusinessException("参数不对");
        }
        if (sort == null) {
            throw new BusinessException("排序值不能为空");
        }
        if (homeBookMapper.updateSort(id, sort) == 0) {
            throw new BusinessException("推荐位不存在");
        }
    }

    @Override
    public void delete(Long id) {
        if (homeBookMapper.deleteById(id) == 0) {
            throw new BusinessException("推荐位不存在");
        }
    }

    /**
     * 最新更新：按作品更新时间倒序，取已上架的若干本
     * 与推荐位的字段保持一致，前端一套渲染逻辑处理三个板块
     */
    private List<Map<String, Object>> newest() {
        List<BookInfo> books = bookInfoMapper.selectTopByUpdateTime(NEWEST_SIZE);
        List<Map<String, Object>> list = new ArrayList<>(books.size());
        for (BookInfo book : books) {
            Map<String, Object> item = new HashMap<>();
            item.put("bookId", book.getId());
            item.put("bookName", book.getBookName());
            item.put("authorName", book.getAuthorName());
            item.put("coverUrl", book.getCoverUrl());
            item.put("bookDesc", book.getBookDesc());
            item.put("wordCount", book.getWordCount());
            item.put("visitCount", book.getVisitCount());
            item.put("score", book.getScore());
            list.add(item);
        }
        return list;
    }

    private Integer checkSection(Integer section) {
        if (section == null || (section != 1 && section != 2)) {
            throw new BusinessException("板块不存在");
        }
        return section;
    }

}
