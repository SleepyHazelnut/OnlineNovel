package com.inkisle.controller;

import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.dto.RatingDTO;
import com.inkisle.service.BookRatingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 书籍评分
 * 未放在 `/book/**` 下：那段路径整体对游客放行，拦截器不会解析 token，
 * 拿不到当前读者是谁，也就无法记录「我打了几分」
 */
@RestController
@RequestMapping("/rating")
public class RatingController {

    @Autowired
    private BookRatingService bookRatingService;

    /**
     * 提交或修改自己的评分，1-5 星
     */
    @PostMapping("/{bookId}")
    public Result<Map<String, Object>> rate(@PathVariable Long bookId,
                                            @Valid @RequestBody RatingDTO ratingDTO,
                                            HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(bookRatingService.rate(bookId, userId, ratingDTO.getScore()));
    }

    /**
     * 评分汇总与我的评分
     */
    @GetMapping("/{bookId}")
    public Result<Map<String, Object>> summary(@PathVariable Long bookId, HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(bookRatingService.summary(bookId, userId));
    }

}
