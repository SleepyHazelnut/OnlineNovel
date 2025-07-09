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
 * AI 创作助手，作者撰写正文时使用
 * 拦截器按路径前缀判断角色，故挂在 `/author` 前缀下，
 * 由前缀规则自动拦截读者，无需为独立路径单独维护特判规则
 */
@RestController
@RequestMapping("/author/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * 润色 / 扩写 / 缩写，具体做哪个看 `type`
     */
    @PostMapping("/rewrite")
    public Result<String> rewrite(@Valid @RequestBody AiRewriteDTO dto) {
        return Result.ok(aiService.rewrite(dto.getType(), dto.getContent()));
    }

}
