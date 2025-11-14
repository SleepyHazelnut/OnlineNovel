package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.PointBizType;
import com.inkisle.common.Result;
import com.inkisle.entity.BookChapter;
import com.inkisle.entity.UserReadHistory;
import com.inkisle.service.BookChapterService;
import com.inkisle.service.UserReadHistoryService;
import com.inkisle.service.BookService;
import com.inkisle.service.support.PointReporter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 阅读记录，userId 一律取登录态，请求体里传的丢掉 */
@RestController
@RequestMapping("/history")
public class ReadHistoryController {

    @Autowired
    private UserReadHistoryService userReadHistoryService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BookChapterService bookChapterService;

    @Autowired
    private PointReporter pointReporter;

    @PostMapping("/save")
    public Result<Void> save(@RequestBody UserReadHistory history, HttpServletRequest request) {
        // 保存记录会顺带给榜单加热度，不拦的话可以拿未过审的书反复刷榜
        if (!bookService.isVisibleToReader(history.getBookId())) {
            return Result.fail("作品不存在或未上架");
        }
        // 章节也要校验：只验 bookId 的话，客户端可以给任意 bookId 编个章节号，
        // 反复调这个接口把热度刷上去，接上积分之后还能顺带刷分
        BookChapter chapter = bookChapterService.getVisibleChapter(history.getBookId(), history.getChapterId());

        Long userId = CurrentUser.id(request);
        history.setUserId(userId);
        // 章节号以库里为准，不信请求体
        history.setChapterNum(chapter.getChapterNum());
        userReadHistoryService.save(history);

        pointReporter.awardAfterCommit(userId, PointBizType.READ_CHAPTER, chapter.getId(), null,
                "第 " + chapter.getChapterNum() + " 章");
        return Result.ok();
    }

    @GetMapping("/list")
    public Result<List<UserReadHistory>> list(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(userReadHistoryService.listByUserId(userId));
    }

    @GetMapping("/last")
    public Result<UserReadHistory> last(@RequestParam Long bookId, HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(userReadHistoryService.lastRead(userId, bookId));
    }

}
