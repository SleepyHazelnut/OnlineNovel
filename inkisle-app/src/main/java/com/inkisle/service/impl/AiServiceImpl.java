package com.inkisle.service.impl;

import com.inkisle.common.BusinessException;
import com.inkisle.service.AiService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 创作助手
 * <p>
 * 润色、扩写、缩写各对应一句提示词，统一集中于此，
 * 调整语气或措辞只需修改此处，无需改动业务代码
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Map<String, String> PROMPTS = Map.of(
            "polish", "你是一位小说编辑。请润色下面这段文字：保持原意和大致篇幅，"
                    + "让表达更顺、更有画面感。不要添加任何解释，直接输出改写后的正文。",
            "expand", "你是一位小说作者。请把下面这段文字扩写到原来的两倍左右："
                    + "补充细节和环境描写，保持人称、时态和语气一致。不要添加任何解释，直接输出改写后的正文。",
            "shrink", "你是一位小说编辑。请把下面这段文字压缩到原来的一半左右："
                    + "保留关键情节和人物，删掉重复的描写。不要添加任何解释，直接输出改写后的正文。"
    );

    private final ChatClient chatClient;

    public AiServiceImpl(ChatClient.Builder builder) {
        // ChatClient 由 Builder 构建，Spring AI 自动配置会注入该 Builder
        this.chatClient = builder.build();
    }

    @Override
    public String rewrite(String type, String content) {
        String instruction = PROMPTS.get(type);
        if (instruction == null) {
            throw new BusinessException("不支持的处理方式");
        }

        // 提示词与原文拼接为一条 user 消息发送，未单独使用 system 角色
        return chatClient.prompt()
                .user(instruction + "\n\n原文：\n" + content)
                .call()
                .content();
    }

}
