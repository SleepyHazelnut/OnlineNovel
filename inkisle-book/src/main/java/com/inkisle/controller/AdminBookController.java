package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的作品管理
 * 与作者端的删除分开：那条路径校验归属，管理员需要能清理任意作品
 */
@RestController
@RequestMapping("/admin/book")
public class AdminBookController {

    @Autowired
    private BookService bookService;

    /**
     * 删除作品，连同章节、正文、评论、评分、推荐位、收藏与阅读记录一并清理
     * 不可恢复，前端须二次确认
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookService.adminDelete(id);
        return Result.ok();
    }

}
