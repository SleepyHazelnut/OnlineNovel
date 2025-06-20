package com.inkisle.service;

/**
 * AI 创作助手
 * 润色、扩写、缩写三种改写逻辑相同，仅提示词不同，
 * 故对外只提供 `rewrite`，用 `type` 区分
 */
public interface AiService {

    /**
     * 按指定方式改写一段文字
     *
     * @param type    polish / expand / shrink
     * @param content 原文
     * @return 改写结果
     */
    String rewrite(String type, String content);

}
