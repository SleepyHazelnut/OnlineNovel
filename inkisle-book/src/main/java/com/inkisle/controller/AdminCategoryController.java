package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.dto.CategorySaveDTO;
import com.inkisle.service.BookCategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端的分类维护
 * 读者端只读的树还在 /category/list，分开挂，别让写接口落在游客能走的路径上
 */
@RestController
@RequestMapping("/admin/category")
public class AdminCategoryController {

    @Autowired
    private BookCategoryService bookCategoryService;

    @PostMapping
    public Result<Void> create(@Valid @RequestBody CategorySaveDTO dto) {
        bookCategoryService.create(dto);
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CategorySaveDTO dto) {
        bookCategoryService.update(id, dto);
        return Result.ok();
    }

    /**
     * 删除分类。存在子分类或仍有作品引用时由服务层拒绝
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        bookCategoryService.delete(id);
        return Result.ok();
    }

}
