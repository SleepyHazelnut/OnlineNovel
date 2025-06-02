package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.entity.BookCategory;
import com.inkisle.service.BookCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private BookCategoryService bookCategoryService;

    /**
     * 分类列表，树形返回
     */
    @GetMapping("/list")
    public Result list() {
        List<BookCategory> tree = bookCategoryService.tree();
        return Result.ok(tree);
    }

}
