package com.inkisle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 配置首页推荐位
 */
@Data
public class HomeBookDTO {

    @NotNull(message = "请选择作品")
    private Long bookId;

    /** 1 热门推荐，2 精品推荐 */
    @NotNull(message = "请选择板块")
    @Min(value = 1, message = "板块不存在")
    @Max(value = 2, message = "板块不存在")
    private Integer section;

    /** 排序，越大越靠前；不传按 0 处理 */
    private Integer sort;

}
