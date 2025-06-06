package com.inkisle.entity;

import lombok.Data;

/**
 * 章节正文
 * 正文单独建表，仅在读取某一章时按 chapterId 查询
 * 与章节合并建表时，查询目录会一并取出正文，拖慢列表
 */
@Data
public class BookContent {

    private Long id;

    private Long chapterId;

    private String content;

}
