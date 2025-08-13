package com.inkisle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 作者保存作品的入参
 * 新增和修改共用，id 为空为新增，有值为修改
 */
@Data
public class BookSaveDTO {

    /** 为空表示新增，有值表示修改 */
    private Long id;

    @NotBlank(message = "书名不能为空")
    @Size(max = 100, message = "书名最多 100 个字")
    private String bookName;

    private Long categoryId;

    @Size(max = 500, message = "简介最多 500 个字")
    private String bookDesc;

    private String coverUrl;

    /** 不填则取作者笔名 */
    private String authorName;

}
