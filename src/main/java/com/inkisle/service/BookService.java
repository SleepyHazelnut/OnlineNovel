package com.inkisle.service;

import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.BookInfo;

import java.util.List;
import java.util.Map;

public interface BookService {

    /**
     * 书籍分页列表，categoryId 为空时返回全部
     */
    Map<String, Object> page(Integer pageNum, Integer pageSize, Long categoryId);

    BookInfo getById(Long id);

    /**
     * 搜索书籍，书名或作者命中即可
     */
    Map<String, Object> search(Integer pageNum, Integer pageSize, String keyword);

    /**
     * 作者名下的全部作品
     */
    List<BookInfo> listByAuthor(Long authorId);

    /**
     * 新增或修改作品，改的时候要确认这本书是当前作者的
     */
    void save(BookSaveDTO bookSaveDTO, Long authorId);

    /**
     * 删除作品，同样只能删自己的
     */
    void delete(Long id, Long authorId);

    /**
     * 改连载 / 完结
     */
    void updateStatus(Long id, Integer status, Long authorId);

    /**
     * 作品数据：阅读数、收藏数、评论数
     */
    Map<String, Object> stat(Long bookId, Long authorId);

}
