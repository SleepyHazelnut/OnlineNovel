package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookInfo;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/book")
public class BookController {

    @Autowired
    private BookService bookService;

    /**
     * 书籍列表，分页返回；传了 categoryId 就按分类筛
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) Long categoryId) {
        return Result.ok(bookService.page(pageNum, pageSize, categoryId));
    }

    /**
     * 搜索书籍
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam String keyword,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(bookService.search(pageNum, pageSize, keyword));
    }

    /**
     * 书籍详情
     */
    @GetMapping("/{id}")
    public Result<BookInfo> detail(@PathVariable Long id) {
        return Result.ok(bookService.getById(id));
    }

}
