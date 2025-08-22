package com.inkisle.controller;

import com.inkisle.common.BookAuditStatus;
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
     * 书籍列表，分页返回；传入 categoryId 时按分类筛选，不传则为全部
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            @RequestParam(required = false) Long categoryId) {
        return Result.ok(bookService.page(pageNum, pageSize, categoryId));
    }

    /**
     * 搜索书籍，也是分页返回
     */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam String keyword,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(bookService.search(pageNum, pageSize, keyword));
    }

    /** 详情。未过审的作品读者无法查看，只在列表过滤无效，知道 id 仍能访问 */
    @GetMapping("/{id}")
    public Result<BookInfo> detail(@PathVariable Long id) {
        BookInfo book = bookService.getById(id);
        if (book == null || !BookAuditStatus.isVisible(book.getAuditStatus())) {
            return Result.fail("作品不存在或未上架");
        }
        return Result.ok(book);
    }

}
