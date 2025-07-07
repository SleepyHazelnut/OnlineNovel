package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.entity.UserBookshelf;
import com.inkisle.service.UserBookshelfService;
import com.inkisle.service.BookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bookshelf")
public class BookshelfController {

    @Autowired
    private UserBookshelfService userBookshelfService;

    @Autowired
    private BookService bookService;

    /**
     * 加入书架
     */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long bookId = params.get("bookId");
        // 未上架的作品不允许加入，否则书架中会出现无法打开详情页的作品
        if (!bookService.isVisibleToReader(bookId)) {
            return Result.fail("作品不存在或未上架");
        }
        Long userId = CurrentUser.id(request);
        userBookshelfService.add(userId, bookId);
        return Result.ok();
    }

    /**
     * 移出书架
     */
    @PostMapping("/remove")
    public Result<Void> remove(@RequestBody Map<String, Long> params, HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        userBookshelfService.remove(userId, params.get("bookId"));
        return Result.ok();
    }

    /**
     * 当前用户的书架
     */
    @GetMapping("/list")
    public Result<List<UserBookshelf>> list(HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(userBookshelfService.listByUserId(userId));
    }

    /**
     * 查询当前用户是否收藏该书，详情页据此显示不同按钮
     */
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam Long bookId, HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(userBookshelfService.isCollected(userId, bookId));
    }

}
