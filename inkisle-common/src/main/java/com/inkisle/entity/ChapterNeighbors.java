package com.inkisle.entity;

import lombok.Data;

/**
 * 一章的上下章
 * 阅读页每翻一章都要知道前后各是谁，两条独立的 SELECT 合成一次往返。
 * 不写成 chapter_num = 当前值 ± 1：章节可删除、编号会跳号，
 * 按大小取相邻一条才可靠
 */
@Data
public class ChapterNeighbors {

    private Long prevId;

    private Long nextId;

}
