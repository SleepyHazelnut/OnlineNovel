package com.inkisle.controller;

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

    /**
     * 评论列表，游客也能看
     * 登录读者额外能看到自己待审的评论，否则发完立刻消失，会以为没发出去
     */
    @GetMapping("/list")
    public Result<List<BookComment>> list(@RequestParam Long bookId, HttpServletRequest request) {
        // 与书籍详情同一口径：作品未过审时评论一并隐藏
        if (!bookService.isVisibleToReader(bookId)) {
            return Result.fail("作品不存在或未上架");
        }
        return Result.ok(bookCommentService.listByBookId(bookId, optionalReaderId(request)));
    }

    /**
     * 可选解析读者身份
     * `/comment/list` 在拦截器的放行名单里，那里不会解析 token，
     * 这里自行解一次且失败不影响匿名访问：token 过期或缺失都按游客处理
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
        // 作者端、管理端的 sub 是另外两张表的主键，不能当作读者 id 使用
        if (!RoleConstant.USER.equals(claims.get("role", String.class))) {
            return null;
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 发表评论或回复，要登录
     */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody BookComment comment, HttpServletRequest request) {
        if (comment.getBookId() == null) {
            return Result.fail("缺少书籍 ID");
        }
        if (!bookService.isVisibleToReader(comment.getBookId())) {
            return Result.fail("作品不存在或未上架");
        }
        Long userId = (Long) request.getAttribute("userId");
        // 以登录态的 userId 覆盖请求体中携带的值，不接收前端传入
        comment.setUserId(userId);
        bookCommentService.add(comment);
        return Result.ok();
    }

    /**
     * 我的评论，分页返回，按时间倒序
     */
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize,
                                            HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.ok(bookCommentService.pageByUser(userId, pageNum, pageSize));
    }

    /**
     * 修改自己的评论
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody CommentEditDTO dto,
                               HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        bookCommentService.updateContent(id, userId, dto.getContent());
        return Result.ok();
    }

    /**
     * 删除自己的评论。归属在服务层按库中记录校验，不信任前端
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        bookCommentService.delete(id, userId);
        return Result.ok();
    }

}
