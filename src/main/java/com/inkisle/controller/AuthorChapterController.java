package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookChapter;
import com.inkisle.service.BookChapterService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 作者端的章节管理
 * 与作品管理一致，这里只校验章节归属，不校验作品是否过审：
 * 草稿本身需要可编辑，不应被读者端的可见性规则限制
 */
@RestController
@RequestMapping("/author/chapter")
public class AuthorChapterController {

    @Autowired
    private BookChapterService bookChapterService;

    /**
     * 某本书的章节列表，编辑页要用
     */
    @GetMapping("/list")
    public Result<List<BookChapter>> list(@RequestParam Long bookId, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookChapterService.listByBookIdForAuthor(bookId, authorId));
    }

    /**
     * 作者预览章节正文
     * 单独实现而不复用读者端的 `/chapter/{id}`：该路径会拦截未过审的作品，
     * 而作者需要预览自己的草稿，故另开一条路径
     */
    @GetMapping("/read")
    public Result<Map<String, Object>> read(@RequestParam Long id, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookChapterService.readForAuthor(id, authorId));
    }

    /**
     * 保存章节，带 id 是改，不带是新增；正文单独放 content 字段里一起传
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");

        BookChapter chapter = new BookChapter();
        if (params.get("id") != null) {
            chapter.setId(Long.valueOf(params.get("id").toString()));
        }
        chapter.setBookId(Long.valueOf(params.get("bookId").toString()));
        chapter.setChapterName((String) params.get("chapterName"));

        bookChapterService.saveChapter(chapter, (String) params.get("content"), authorId);
        return Result.ok();
    }

    /**
     * 删除章节
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        bookChapterService.deleteChapter(params.get("id"), authorId);
        return Result.ok();
    }

    /**
     * 导入整本 TXT，按「第X章」自动切分
     */
    @PostMapping("/import")
    public Result<Integer> importTxt(@RequestParam("file") MultipartFile file,
                                     @RequestParam Long bookId,
                                     HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        int count = bookChapterService.importChapters(bookId, file, authorId);
        return Result.ok(count);
    }

}
