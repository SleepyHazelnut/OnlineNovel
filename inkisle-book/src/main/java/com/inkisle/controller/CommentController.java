package com.inkisle.controller;

import com.inkisle.annotation.RepeatSubmit;
import com.inkisle.common.CurrentUser;
import com.inkisle.common.Result;
import com.inkisle.common.RoleConstant;
import com.inkisle.dto.CommentEditDTO;
import com.inkisle.entity.BookComment;
import com.inkisle.service.BookCommentService;
import com.inkisle.service.BookService;
import com.inkisle.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private BookCommentService bookCommentService;

    @Autowired
    private BookService bookService;

    @Autowired
    private JwtUtil jwtUtil;

    /** 游客也能看；登录读者额外能看到自己待审的那几条 */
    @GetMapping("/list")
    public Result<List<BookComment>> list(@RequestParam Long bookId, HttpServletRequest request) {
        // 与书籍详情同一口径，作品没过审评论也一起藏起来
        if (!bookService.isVisibleToReader(bookId)) {
            return Result.fail("作品不存在或未上架");
        }
        return Result.ok(bookCommentService.listByBookId(bookId, optionalReaderId(request)));
    }

    /**
     * /comment/list 在拦截器放行名单里，拿不到 userId，这里自己解一次；
     * 解析失败按游客处理，匿名访问不受影响
     */
    private Long optionalReaderId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        Claims claims = jwtUtil.parse(header.substring("Bearer ".length()));
        if (claims == null) {
            return null;
        }
        // 作者、管理的 sub 是另外两张表的主键，不能当读者 id 用
        if (!RoleConstant.USER.equals(claims.get("role", String.class))) {
            return null;
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 锁按书分：同一用户连评两本书是两个动作，不该互相挡 */
    @RepeatSubmit(key = "#p0.bookId")
    @PostMapping("/add")
    public Result<Void> add(@RequestBody BookComment comment, HttpServletRequest request) {
        if (comment.getBookId() == null) {
            return Result.fail("缺少书籍 ID");
        }
        if (!bookService.isVisibleToReader(comment.getBookId())) {
            return Result.fail("作品不存在或未上架");
        }
        Long userId = CurrentUser.id(request);
        // 归属以登录态为准，不用请求体里的
        comment.setUserId(userId);
        bookCommentService.add(comment);
        return Result.ok();
    }

    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        return Result.ok(bookCommentService.pageByUser(userId, pageNum, pageSize));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody CommentEditDTO dto,
                               HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        bookCommentService.updateContent(id, userId, dto.getContent());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = CurrentUser.id(request);
        bookCommentService.delete(id, userId);
        return Result.ok();
    }

}
