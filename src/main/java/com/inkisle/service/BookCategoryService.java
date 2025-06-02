package com.inkisle.service;

import com.inkisle.entity.BookCategory;

import java.util.List;

public interface BookCategoryService {

    /**
     * 分类树，返回一级分类，每项通过 `children` 携带子分类
     */
    List<BookCategory> tree();

}
