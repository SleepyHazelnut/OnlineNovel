package com.inkisle.controller;

import com.inkisle.entity.BookAuditRecord;
import com.inkisle.service.BookAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给书籍服务用的内部接口，送审、重审、查结论
 * 网关不转发 /internal，拦截器里放行，服务之间调不带用户 token
 */
@RestController
@RequestMapping("/internal/audit")
public class InternalAuditController {

    @Autowired
    private BookAuditService bookAuditService;

    @PostMapping("/book/{bookId}/submit")
    public void submit(@PathVariable Long bookId, @RequestParam Long authorId) {
        bookAuditService.submit(bookId, authorId);
    }

    @PostMapping("/book/{bookId}/resubmit")
    public void resubmitAfterEdit(@PathVariable Long bookId, @RequestParam Long authorId) {
        bookAuditService.resubmitAfterEdit(bookId, authorId);
    }

    @GetMapping("/book/{bookId}/latest")
    public BookAuditRecord latestAudit(@PathVariable Long bookId, @RequestParam Long authorId) {
        return bookAuditService.latestAuditForAuthor(bookId, authorId);
    }

    @PostMapping("/book/{bookId}/offline")
    public void offlineByAuthor(@PathVariable Long bookId, @RequestParam Long authorId) {
        bookAuditService.offlineByAuthor(bookId, authorId);
    }

    @DeleteMapping("/book/{bookId}")
    public void deleteByBookId(@PathVariable Long bookId) {
        bookAuditService.deleteByBookId(bookId);
    }

}
