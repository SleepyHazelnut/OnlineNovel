package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
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

/** 作者端作品管理，authorId 一律取自 token */
@RestController
@RequestMapping("/author/book")
public class AuthorBookController {

    @Autowired
    private BookService bookService;

    @Autowired
    private BookAuditService bookAuditService;

    @GetMapping("/list")
    public Result<List<BookInfo>> list(HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        return Result.ok(bookService.listByAuthor(authorId));
    }

    /** 驳回后还要能打开原稿，所以不走读者端的 /book/{id} */
    @GetMapping("/detail")
    public Result<BookInfo> detail(@RequestParam Long id, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        return Result.ok(bookService.getForAuthor(id, authorId));
    }

    @PostMapping("/save")
    public Result<Void> save(@Valid @RequestBody BookSaveDTO bookSaveDTO, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        bookService.save(bookSaveDTO, authorId);
        return Result.ok();
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        bookService.delete(params.get("id"), authorId);
        return Result.ok();
    }

    @PostMapping("/status")
    public Result<Void> updateStatus(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        Long id = Long.valueOf(params.get("id").toString());
        Integer status = Integer.valueOf(params.get("status").toString());
        bookService.updateStatus(id, status, authorId);
        return Result.ok();
    }

    @GetMapping("/stat")
    public Result<Map<String, Object>> stat(@RequestParam Long bookId, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        return Result.ok(bookService.stat(bookId, authorId));
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        bookAuditService.submit(params.get("bookId"), authorId);
        return Result.ok();
    }

    @GetMapping("/audit")
    public Result<BookAuditRecord> audit(@RequestParam Long bookId, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        return Result.ok(bookAuditService.latestAuditForAuthor(bookId, authorId));
    }

    /** 下架后读者端立刻不可见，想回来走 /author/book/submit 重新送审 */
    @PostMapping("/offline")
    public Result<Void> offline(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = CurrentUser.id(request);
        bookAuditService.offlineByAuthor(params.get("bookId"), authorId);
        return Result.ok();
    }

}
