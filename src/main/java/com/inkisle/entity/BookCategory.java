package com.inkisle.entity;

import lombok.Data;

import java.util.List;

/**
 * 书籍分类
 * 仅一级、二级两层，子分类存放于 children，通过 parentId 关联
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
