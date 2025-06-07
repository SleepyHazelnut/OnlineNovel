package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookChapter;
import com.inkisle.service.BookChapterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/chapter")
public class ChapterController {

    @Autowired
    private BookChapterService bookChapterService;

    /**
     * 章节目录
     */
    @GetMapping("/list/{bookId}")
    public Result list(@PathVariable Long bookId) {
        List<BookChapter> chapters = bookChapterService.listByBookId(bookId);
        return Result.ok(chapters);
    }

    /**
     * 读某一章，带上上一章、下一章的ID
     */
    @GetMapping("/{id}")
    public Result read(@PathVariable Long id) {
        return Result.ok(bookChapterService.read(id));
    }

}
