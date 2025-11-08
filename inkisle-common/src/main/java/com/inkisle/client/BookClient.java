package com.inkisle.client;

import com.inkisle.dto.AuditStatusDTO;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.BookInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 审核服务改作品、章节状态时用它
 * 状态列在书籍库里，审核服务不再直连那几张表
 */
@FeignClient(name = "inkisle-book")
public interface BookClient {

    @GetMapping("/internal/book/{id}")
    BookInfo getBook(@PathVariable("id") Long id);

    /**
     * 管理端的作品列表。只查已上架和已下架两种，待审的走审核记录
     */
    @GetMapping("/internal/book/list")
    Map<String, Object> listByAuditStatus(@RequestParam("status") Integer status,
                                          @RequestParam("offset") int offset,
                                          @RequestParam("size") int size);

    /**
     * 置为待审。返回影响行数，0 说明别人已经提交过了
     */
    @PostMapping("/internal/book/{id}/pending")
    Integer markPending(@PathVariable("id") Long id);

    @PostMapping("/internal/book/{id}/audit-status")
    void updateBookStatus(@PathVariable("id") Long id, @RequestBody AuditStatusDTO dto);

    /**
     * 审核通过：改作品状态 + 整本章节放行，在一次调用、一笔事务里完成。
     * 拆成两次调用的话，第二步失败会留下「书已上架但章节全不可见」的状态
     */
    @PostMapping("/internal/book/{id}/approve")
    void approveBook(@PathVariable("id") Long id);

    @GetMapping("/internal/book/{id}/chapters")
    List<BookChapter> listChapters(@PathVariable("id") Long id);

    /** 章节审核的待审列表，按状态分页 */
    @GetMapping("/internal/chapter/list")
    Map<String, Object> listChaptersByStatus(@RequestParam("status") Integer status,
                                             @RequestParam("offset") int offset,
                                             @RequestParam("size") int size);

    @PostMapping("/internal/book/{id}/chapters/pass-all")
    void passAllChapters(@PathVariable("id") Long id);

    @GetMapping("/internal/chapter/{id}")
    BookChapter getChapter(@PathVariable("id") Long id);

    @PostMapping("/internal/chapter/{id}/audit-status")
    void updateChapterStatus(@PathVariable("id") Long id, @RequestBody AuditStatusDTO dto);

    @GetMapping("/internal/chapter/{id}/content")
    Map<String, Object> getChapterContent(@PathVariable("id") Long id);

}
