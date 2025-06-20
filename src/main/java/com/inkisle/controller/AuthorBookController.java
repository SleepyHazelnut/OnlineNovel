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
 */
@RestController
@RequestMapping("/author/book")
public class AuthorBookController {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookAuditService bookAuditService;

    /**
     * 我的作品
     */
    @GetMapping("/list")
    public Result<List<BookInfo>> list(HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookService.listByAuthor(authorId));
    }

    /**
     * 保存作品，带 id 是修改，不带是新增
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
     * 连载 / 完结 切换，作者自己控制
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
     * 手快连点两下会送出两条待审记录，这里上个锁挡一下
     */
    @RepeatSubmit
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        bookAuditService.submit(params.get("bookId"), authorId);
        return Result.ok();
    }

    /**
     * 看一眼这本书最近一次审核的结果
     */
    @GetMapping("/audit")
    public Result<BookAuditRecord> audit(@RequestParam Long bookId) {
        return Result.ok(bookAuditService.latestAudit(bookId));
    }

}
