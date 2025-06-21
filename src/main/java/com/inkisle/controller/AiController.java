package com.inkisle.controller;

import com.inkisle.common.Result;
import com.inkisle.dto.AiRewriteDTO;
import com.inkisle.service.AiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 创作助手，作者写正文时用
 */
@RestController
@RequestMapping("/author/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * 润色 / 扩写 / 缩写
     */
    @PostMapping("/rewrite")
    public Result<String> rewrite(@Valid @RequestBody AiRewriteDTO dto) {
        return Result.ok(aiService.rewrite(dto.getType(), dto.getContent()));
    }

}
