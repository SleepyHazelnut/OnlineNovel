package com.inkisle.dto;

import lombok.Data;

/**
 * 只要一句说明的提交体，驳回、下架这类操作共用
 */
@Data
public class RemarkDTO {

    private String remark;

}
