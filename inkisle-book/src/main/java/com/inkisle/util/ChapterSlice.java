package com.inkisle.util;

/**
 * 切分出来的一章：标题和正文
 *
 * 用列表承载而不是 Map：TXT 里出现同名标题（分卷重复、作者笔误）时，
 * Map 会把后一章覆盖前一章，正文直接丢掉，而且返回的章节数也是错的
 */
public record ChapterSlice(String title, String content) {
}
