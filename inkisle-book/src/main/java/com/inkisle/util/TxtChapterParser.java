package com.inkisle.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把整本 TXT 按「第X章」这样的标题行切成若干章，作者导入整本小说用
 */
public class TxtChapterParser {

    /**
     * 章节标题行：第 + 数字或中文数字 + 章 / 节 / 回 / 卷
     */
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "^\\s*第\\s*[0-9零一二三四五六七八九十百千]+\\s*[章节回卷][^\\n]*$",
            Pattern.MULTILINE);

    /**
     * 切成有序的章节切片
     *
     * 早先用的是 标题 -> 正文 的 Map，遇到同名标题会静默覆盖，
     * 一本分卷命名重复的书导进来就少几章正文，而且返回条数也跟着变小，用户看不出异常。
     * 章序本来就由出现顺序决定，列表天然满足，不需要再靠 Map 保序
     */
    public static List<ChapterSlice> split(String text) {
        List<ChapterSlice> chapters = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chapters;
        }

        Matcher matcher = TITLE_PATTERN.matcher(text);
        int lastTitleEnd = -1;
        String lastTitle = null;

        while (matcher.find()) {
            if (lastTitle != null) {
                chapters.add(new ChapterSlice(lastTitle,
                        text.substring(lastTitleEnd, matcher.start()).trim()));
            }
            lastTitle = matcher.group().trim();
            lastTitleEnd = matcher.end();
        }

        // 最后一章的内容一直到文件结尾
        if (lastTitle != null) {
            chapters.add(new ChapterSlice(lastTitle, text.substring(lastTitleEnd).trim()));
        }

        // 未识别出任何标题行时，将整篇作为一章，避免导入为空
        if (chapters.isEmpty()) {
            chapters.add(new ChapterSlice("正文", text.trim()));
        }

        return chapters;
    }

}
