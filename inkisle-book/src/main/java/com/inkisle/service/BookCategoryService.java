package com.inkisle.service;

import com.inkisle.dto.CategorySaveDTO;
import com.inkisle.entity.BookCategory;

import java.util.List;

public interface BookCategoryService {

    /**
     * 分类树，返回一级分类，子分类挂在 children 上
     */
    List<BookCategory> tree();

    /**
     * 新增分类。只支持两级，父分类必须是一级分类
     */
    void create(CategorySaveDTO dto);

    /**
     * 修改分类。改动后仍要保持两级，不允许把分类挂到自己的子分类下
     */
    void update(Long id, CategorySaveDTO dto);

    /**
     * 删除分类。存在子分类或仍有作品引用时拒绝删除
     */
    void delete(Long id);

}
