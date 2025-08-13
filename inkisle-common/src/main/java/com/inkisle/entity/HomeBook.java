package com.inkisle.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页推荐位
 * 只记 book_id 和属于哪个板块，书名封面联表取
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
