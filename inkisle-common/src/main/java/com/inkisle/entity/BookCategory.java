package com.inkisle.entity;

import lombok.Data;

import java.util.List;

/**
 * 书籍分类
 * 就两级，子分类放 children 里，靠 parentId 关联
 */
@Data
public class BookCategory {

    private Long id;

    /** 分类名 */
    private String categoryName;

    /** 父级分类ID，0 表示一级分类 */
    private Long parentId;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 子分类，只有一级分类会带上 */
    private List<BookCategory> children;

}
