package com.inkisle.util;

import java.util.LinkedHashMap;
import java.util.Map;
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
     * 切成 章节标题 -> 正文
     * 用 LinkedHashMap，换成 HashMap 遍历顺序不确定，导入后章节会乱序
     */
    public static Map<String, String> split(String text) {
        Map<String, String> chapters = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return chapters;
        }

        Matcher matcher = TITLE_PATTERN.matcher(text);
        int lastTitleEnd = -1;
        String lastTitle = null;

        while (matcher.find()) {
            if (lastTitle != null) {
                chapters.put(lastTitle, text.substring(lastTitleEnd, matcher.start()).trim());
            }
            lastTitle = matcher.group().trim();
            lastTitleEnd = matcher.end();
        }

        // 最后一章的内容一直到文件结尾
        if (lastTitle != null) {
            chapters.put(lastTitle, text.substring(lastTitleEnd).trim());
        }

        // 未识别出任何标题行时，将整篇作为一章，避免导入为空
        if (chapters.isEmpty()) {
            chapters.put("正文", text.trim());
        }

        return chapters;
    }

}
