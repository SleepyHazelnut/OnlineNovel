package com.inkisle.service.impl;

import com.inkisle.entity.BookCategory;
import com.inkisle.mapper.BookCategoryMapper;
import com.inkisle.service.BookCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类树
 * 分类为两级，`parent_id` 为 0 的为顶层
 * 数据量仅数十条，故一次全部查出，在内存中组装 `children`
 */
@Service
public class BookCategoryServiceImpl implements BookCategoryService {

    /** 一级分类的 parent_id 约定为 0 */
    private static final long ROOT_ID = 0L;

    @Autowired
    private BookCategoryMapper bookCategoryMapper;

    @Override
    public List<BookCategory> tree() {
        List<BookCategory> all = bookCategoryMapper.selectAll();

        List<BookCategory> roots = new ArrayList<>();
        for (BookCategory category : all) {
            if (category.getParentId() == ROOT_ID) {
                roots.add(category);
            }
        }

        // 分类仅数十条，内存中双层循环即可完成组装，无需为此数据量再查库
        for (BookCategory root : roots) {
            List<BookCategory> children = new ArrayList<>();
            for (BookCategory category : all) {
                if (category.getParentId().equals(root.getId())) {
                    children.add(category);
                }
            }
            root.setChildren(children);
        }
        return roots;
    }

}
