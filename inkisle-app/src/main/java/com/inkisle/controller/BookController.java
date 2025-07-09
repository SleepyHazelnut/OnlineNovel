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
     * 书籍列表，分页返回；带了 categoryId 就再按分类筛一道，不带就是全部
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

    /**
     * 书籍详情
     * 审核未通过的作品读者不可见：列表已过滤，详情接口同样要判，否则知道 id 仍可直接访问
     * 校验放在 controller 层而非 mapper：`selectById` 同时被作者提交审核的链路复用，
     * SQL 里加过滤会导致被驳回的作品重新提交时报「作品不存在」
     * <p>
     * 直接用取到的实体判断，不再调 isVisibleToReader 回库一次：
     * 那等于同一行查两遍。状态变化时审核流程会清掉这条缓存，读到的不会是旧状态
     */
    @GetMapping("/{id}")
    public Result<BookInfo> detail(@PathVariable Long id) {
        BookInfo book = bookService.getById(id);
        if (book == null || !BookAuditStatus.isVisible(book.getAuditStatus())) {
            return Result.fail("作品不存在或未上架");
        }
        return Result.ok(book);
    }

}
