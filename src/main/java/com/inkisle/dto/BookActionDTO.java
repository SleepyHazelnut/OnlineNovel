package com.inkisle.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 已上架作品的下架与恢复。下架要写原因，恢复不用
 */
@Data
public class BookActionDTO {

    @NotNull(message = "作品 id 不能为空")
    private Long bookId;

    private String remark;

}
