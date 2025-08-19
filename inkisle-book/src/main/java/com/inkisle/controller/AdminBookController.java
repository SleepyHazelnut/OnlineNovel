package com.inkisle.controller;

import com.inkisle.common.BusinessException;
import com.inkisle.common.Result;
import com.inkisle.config.SearchProperties;
import com.inkisle.search.BookSearchIndexer;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的作品管理
 * 删书跟作者端那条分开，那条验归属，管理员要能删任何一本
 */
@RestController
@RequestMapping("/admin/book")
public class AdminBookController {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookSearchIndexer bookSearchIndexer;

    @Autowired
    private SearchProperties searchProperties;

    /**
     * 删作品，章节 正文 评论 评分 推荐位 收藏 阅读记录一起清掉
     * 删了就回不来，前端得二次确认
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.adminDelete(id);
        return Result.ok();
    }

    /**
     * 全量重建搜索索引，返回灌了多少本
     * 平时写入时会增量同步，这个接口只有索引写坏或者换了 ES 实例才用
     */
    @PostMapping("/reindex")
    public Result<Integer> reindex() {
        if (!searchProperties.isEs()) {
            throw new BusinessException("当前搜索走 MySQL 全文索引，未启用 ES，无需重建");
        }
        return Result.ok(bookSearchIndexer.rebuild());
    }

}

