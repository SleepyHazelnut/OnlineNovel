package com.inkisle.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TXT 章节切分测试
 * 纯函数，不依赖 Spring 与数据库
 */
class TxtChapterParserTest {

    @Test
    @DisplayName("按「第X章」切分，且顺序与原文一致")
    void splitByChapterTitle() {
        String text = "第一章 开始\n正文一\n第二章 继续\n正文二\n第三章 结束\n正文三";

        Map<String, String> chapters = TxtChapterParser.split(text);

        assertThat(new ArrayList<>(chapters.keySet()))
                .containsExactly("第一章 开始", "第二章 继续", "第三章 结束");
        assertThat(chapters.get("第二章 继续")).isEqualTo("正文二");
    }

    @Test
    @DisplayName("没有标题行时整篇作为一章，而不是返回空")
    void fallbackToSingleChapter() {
        Map<String, String> chapters = TxtChapterParser.split("一段没有任何章节标题的正文");

        assertThat(chapters).hasSize(1);
        assertThat(chapters.get("正文")).isEqualTo("一段没有任何章节标题的正文");
    }

    @Test
    @DisplayName("支持中文数字与节、回、卷")
    void supportChineseNumbers() {
        String text = "第十节 一\n内容\n第二回 二\n内容\n第三卷 三\n内容";

        List<String> titles = new ArrayList<>(TxtChapterParser.split(text).keySet());

        assertThat(titles).containsExactly("第十节 一", "第二回 二", "第三卷 三");
    }

}
