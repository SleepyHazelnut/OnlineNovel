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
 * AI 创作助手，作者写正文的时候用
 * 挂在 /author 下面，拦截器按路径前缀判断角色，前缀本身即可拦截读者，无需额外判断
 */
@RestController
@RequestMapping("/author/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * 润色 扩写 缩写，做哪个看 type
     */
    @PostMapping("/rewrite")
    public Result<String> rewrite(@Valid @RequestBody AiRewriteDTO dto) {
        return Result.ok(aiService.rewrite(dto.getType(), dto.getContent()));
    }

}
