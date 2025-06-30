package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页推荐位
 * 只记书籍 id 与所属板块，展示所需的书名、封面从 book_info 联表取
 */
@Data
public class HomeBook {

    private Long id;

    private Long bookId;

    /** 板块：1 热门推荐，2 精品推荐 */
    private Integer section;

    /** 排序，越大越靠前 */
    private Integer sort;

    private LocalDateTime createTime;

}
