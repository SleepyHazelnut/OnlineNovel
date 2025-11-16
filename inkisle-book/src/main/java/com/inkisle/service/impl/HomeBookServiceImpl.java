package com.inkisle.service.impl;

import com.inkisle.client.PointClient;
import com.inkisle.common.BookAuditStatus;
import com.inkisle.common.BusinessException;
import com.inkisle.dto.BookVotesDTO;
import com.inkisle.dto.HomeBookDTO;
import com.inkisle.entity.BookInfo;
import com.inkisle.entity.HomeBook;
import com.inkisle.mapper.BookInfoMapper;
import com.inkisle.mapper.HomeBookMapper;
import com.inkisle.service.HomeBookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 首页推荐位：热门、精品、最新更新，外加一个读者投票投出来的荐书板块 */
@Slf4j
@Service
public class HomeBookServiceImpl implements HomeBookService {

    /** 最新更新板块的条数，与前端一屏放得下的数量一致 */
    private static final int NEWEST_SIZE = 6;

    /** 荐书板块展示几本 */
    private static final int SPOTLIGHT_SIZE = 6;

    /** 荐书榜多取一些候选，下架的书被过滤后还能凑满 */
    private static final int SPOTLIGHT_POOL = 18;

    @Autowired
    private HomeBookMapper homeBookMapper;

    @Autowired
    private BookInfoMapper bookInfoMapper;

    @Autowired
    private PointClient pointClient;

    @Override
    public List<Map<String, Object>> listBySection(Integer section) {
        return homeBookMapper.selectBySection(checkSection(section));
    }

    /**
     * 三个固定板块，外加一个由读者自己投出来的板块
     * 荐书榜放在最后：它是「社区产生的内容」，优先级低于站方推荐
     */
    @Override
    public Map<String, Object> recommend() {
        Map<String, Object> data = new HashMap<>();
        data.put("hot", homeBookMapper.selectVisibleBySection(1));
        data.put("quality", homeBookMapper.selectVisibleBySection(2));
        data.put("newest", newest());
        data.put("spotlight", spotlight());
        return data;
    }

    /**
     * 读者荐书。票在用户服务，书名封面在本地，所以要一次跨服务取 id、一次本地回填
     *
     * 多取一些候选（SPOTLIGHT_POOL）再截断：票数高的那几本可能刚被下架，
     * 只取 6 个 id 的话过滤完可能只剩 3 本，板块看起来像坏了
     */
    private List<Map<String, Object>> spotlight() {
        List<BookVotesDTO> votes;
        try {
            votes = pointClient.topRecommend(SPOTLIGHT_POOL);
        } catch (Exception e) {
            // 用户服务抖一下不该让整个首页白屏，缺一个板块比缺整个页面好
            log.warn("荐书榜拉取失败，本次不展示该板块", e);
            return List.of();
        }
        if (votes == null || votes.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> voteCount = new LinkedHashMap<>();
        for (BookVotesDTO vote : votes) {
            voteCount.put(vote.getBookId(), vote.getVotes());
        }
        Map<Long, BookInfo> books = new HashMap<>();
        for (BookInfo book : bookInfoMapper.selectByIds(new ArrayList<>(voteCount.keySet()))) {
            books.put(book.getId(), book);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : voteCount.entrySet()) {
            BookInfo book = books.get(entry.getKey());
            // 下架或删掉的书票还在，展示时直接跳过，不清票：重新上架后票数应当还在
            if (book == null || !BookAuditStatus.isVisible(book.getAuditStatus())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("bookId", book.getId());
            item.put("bookName", book.getBookName());
            item.put("authorName", book.getAuthorName());
            item.put("coverUrl", book.getCoverUrl());
            item.put("bookDesc", book.getBookDesc());
            item.put("wordCount", book.getWordCount());
            item.put("visitCount", book.getVisitCount());
            item.put("score", book.getScore());
            item.put("auditStatus", book.getAuditStatus());
            item.put("votes", entry.getValue());
            list.add(item);
            if (list.size() >= SPOTLIGHT_SIZE) {
                break;
            }
        }
        return list;
    }

    @Override
    public void add(HomeBookDTO dto) {
        BookInfo book = bookInfoMapper.selectById(dto.getBookId());
        if (book == null) {
            throw new BusinessException("作品不存在");
        }
        // 未上架的书放进推荐位，读者端也要被过滤掉，等于配了个看不见的条目
        if (!BookAuditStatus.isVisible(book.getAuditStatus())) {
            throw new BusinessException("只有已上架的作品可以上推荐位");
        }
        HomeBook homeBook = new HomeBook();
        homeBook.setBookId(dto.getBookId());
        homeBook.setSection(dto.getSection());
        homeBook.setSort(dto.getSort() == null ? 0 : dto.getSort());
        homeBook.setCreateTime(LocalDateTime.now());
        // 重复配置由唯一键拦，影响 0 行就说明这本书已经在这个板块里
        if (homeBookMapper.insertIgnore(homeBook) == 0) {
            throw new BusinessException("这本书已在该板块");
        }
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
