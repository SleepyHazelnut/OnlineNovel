package com.inkisle.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 审核记录上的操作：通过只需要 id，驳回再加 reason
 */
@Data
public class AuditHandleDTO {

    @NotNull(message = "审核记录 id 不能为空")
    private Long id;

    private String remark;

}
