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

/** 作者端章节管理，只看归属，不卡作品是否过审 */
@RestController
@RequestMapping("/author/chapter")
public class AuthorChapterController {

    @Autowired
    private BookChapterService bookChapterService;

    @GetMapping("/list")
    public Result<List<BookChapter>> list(@RequestParam Long bookId, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookChapterService.listByBookIdForAuthor(bookId, authorId));
    }

    /** 读者端 /chapter/{id} 会拦未过审的作品，作者预览草稿得另开一条 */
    @GetMapping("/read")
    public Result<Map<String, Object>> read(@RequestParam Long id, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        return Result.ok(bookChapterService.readForAuthor(id, authorId));
    }

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

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        bookChapterService.deleteChapter(params.get("id"), authorId);
        return Result.ok();
    }

    @PostMapping("/import")
    public Result<Integer> importTxt(@RequestParam("file") MultipartFile file,
                                     @RequestParam Long bookId,
                                     HttpServletRequest request) {
        Long authorId = (Long) request.getAttribute("userId");
        int count = bookChapterService.importChapters(bookId, file, authorId);
        return Result.ok(count);
    }

}
