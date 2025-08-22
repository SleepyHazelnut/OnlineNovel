package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookChapter;
import com.inkisle.service.BookChapterService;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 章节目录和正文
 * 这两条都按 id 直接读取，只在详情页拦截无效，作品未过审仍能取到正文
 */
@RestController
@RequestMapping("/chapter")
public class ChapterController {

    @Autowired
    private BookChapterService bookChapterService;

    @Autowired
    private BookService bookService;

    @GetMapping("/list/{bookId}")
    public Result<List<BookChapter>> list(@PathVariable Long bookId) {
        if (!bookService.isVisibleToReader(bookId)) {
            return Result.fail("作品不存在或未上架");
        }
        List<BookChapter> chapters = bookChapterService.listByBookId(bookId);
        return Result.ok(chapters);
    }

    /**
     * 读取某一章，同时返回上一章、下一章的 id，供前端翻页
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> read(@PathVariable Long id) {
        return Result.ok(bookChapterService.read(id));
    }

}
