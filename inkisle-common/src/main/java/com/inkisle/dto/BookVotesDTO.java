package com.inkisle.dto;

import lombok.Data;

/**
 * 一本书的荐书票数，首页「读者荐书」板块用
 * 只有 bookId 和票数：书名封面都归书籍服务，跨服务传整本书没意义
 */
@Data
public class BookVotesDTO {

    private Long bookId;

    private Integer votes;

}
