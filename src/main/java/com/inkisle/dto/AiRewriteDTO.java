package com.inkisle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 改写请求
 * AI 改写接口入参：content 为待处理正文，type 为处理方式（润色 / 扩写 / 缩写）
 */
@Data
public class AiRewriteDTO {

    /** 要处理的正文 */
    @NotBlank(message = "内容不能为空")
    @Size(max = 3000, message = "一次最多处理 3000 字")
    private String content;

    /** 处理方式：polish 润色 / expand 扩写 / shrink 缩写 */
    @NotBlank(message = "处理方式不能为空")
    private String type;

}
