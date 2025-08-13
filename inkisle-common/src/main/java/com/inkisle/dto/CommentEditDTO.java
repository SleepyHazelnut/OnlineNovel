package com.inkisle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 改评论内容
 * 只开放正文一项：评论的归属、所在作品与层级都由服务端按 id 从库里取，不接受前端回传
 */
@Data
public class CommentEditDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论内容不能超过 500 字")
    private String content;

}
