package com.inkisle.controller;

import com.inkisle.annotation.RepeatSubmit;
import com.inkisle.common.Result;
import com.inkisle.dto.BookSaveDTO;
import com.inkisle.entity.BookAuditRecord;
import com.inkisle.entity.BookInfo;
import com.inkisle.service.BookAuditService;
import com.inkisle.service.BookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 作者端的作品管理
 * 所有接口均只服务作者查看自己的作品，校验的是作品归属；
 * 与读者端接口是两条独立路径，互不复用
 */
@RestController
@RequestMapping("/author/book")
public class AuthorBookController {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookAuditService bookAuditService;

    /**
     * 当前作者的作品，authorId 只取 token 中的值，忽略请求参数
     */
    @GetMapping("/list")
    public Result<List<BookInfo>> list(HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookService.listByAuthor(authorId));
    }

    /**
     * 自己的作品详情，编辑页回填用
     * 不复用读者端的 `/book/{id}`：该路径会拦截未过审的作品，
     * 作品被驳回后作者点击编辑将无法看到稿件
     */
    @GetMapping("/detail")
    public Result<BookInfo> detail(@RequestParam Long id, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookService.getForAuthor(id, authorId));
    }

    /**
     * 保存作品，带 id 是改，不带是新增
     */
    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody BookSaveDTO bookSaveDTO, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        bookService.save(bookSaveDTO, authorId);
        return Result.ok();
    }

    /**
     * 删除作品
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        bookService.delete(params.get("id"), authorId);
        return Result.ok();
    }

    /**
     * 连载 / 完结 切换，这个作者自己说了算
     */
    @PostMapping("/status")
    public Result<Void> updateStatus(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        Long id = Long.valueOf(params.get("id").toString());
        Integer status = Integer.valueOf(params.get("status").toString());
        bookService.updateStatus(id, status, authorId);
        return Result.ok();
    }

    /**
     * 作品数据统计
     */
    @GetMapping("/stat")
    public Result<Map<String, Object>> stat(@RequestParam Long bookId, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookService.stat(bookId, authorId));
    }

    /**
     * 提交审核，上架前先送审
     * 连续快速点击会提交多条待审记录，故加 `@RepeatSubmit` 拦截
     */
    @RepeatSubmit
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        bookAuditService.submit(params.get("bookId"), authorId);
        return Result.ok();
    }

    /**
     * 查询该书最近一次审核结果，用于了解被驳回的原因
     */
    @GetMapping("/audit")
    public Result<BookAuditRecord> audit(@RequestParam Long bookId) {
        return Result.ok(bookAuditService.latestAudit(bookId));
    }

}
