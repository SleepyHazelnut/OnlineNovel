package com.inkisle.client;

import com.inkisle.entity.BookAuditRecord;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 送审、重审、查审核结论走它。审核记录归审核服务，书籍服务不再插那张表
 */
@FeignClient(name = "inkisle-audit")
public interface AuditClient {

    @PostMapping("/internal/audit/book/{bookId}/submit")
    void submit(@PathVariable("bookId") Long bookId, @RequestParam("authorId") Long authorId);

    /** 改了书名、封面这些展示信息后重新送审 */
    @PostMapping("/internal/audit/book/{bookId}/resubmit")
    void resubmitAfterEdit(@PathVariable("bookId") Long bookId, @RequestParam("authorId") Long authorId);

    @GetMapping("/internal/audit/book/{bookId}/latest")
    BookAuditRecord latestAudit(@PathVariable("bookId") Long bookId, @RequestParam("authorId") Long authorId);

    @PostMapping("/internal/audit/book/{bookId}/offline")
    void offlineByAuthor(@PathVariable("bookId") Long bookId, @RequestParam("authorId") Long authorId);

    /** 作品删除时清掉它的审核记录 */
    @DeleteMapping("/internal/audit/book/{bookId}")
    void deleteByBookId(@PathVariable("bookId") Long bookId);

}
