package com.inkisle.entity;

import lombok.Data;

/**
 * 章节正文
 * 正文单独一张表，读某一章的时候才按 chapterId 去查
 * 跟章节放一张表的话，查目录会把正文也捞出来，列表就慢了
 */
@Data
public class BookContent {

    private Long id;

    private Long chapterId;

    private String content;

}
