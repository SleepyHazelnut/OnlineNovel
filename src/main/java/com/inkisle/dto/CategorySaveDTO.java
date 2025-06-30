package com.inkisle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增或修改分类
 * 只接收名称、父级与排序三项，主键由路径参数指定，不放在请求体里
 */
@Data
public class CategorySaveDTO {

    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名不能超过 50 字")
    private String categoryName;

    /** 父级分类ID，为空按一级分类处理 */
    private Long parentId;

    /** 排序值，越小越靠前，为空按 0 处理 */
    private Integer sort;

}
