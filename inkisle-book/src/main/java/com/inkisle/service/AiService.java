package com.inkisle.service;

/**
 * AI 创作助手。润色、扩写、缩写逻辑一样，只是提示词不同，所以只开一个 rewrite
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
