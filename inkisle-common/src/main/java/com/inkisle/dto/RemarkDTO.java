package com.inkisle.dto;

import lombok.Data;

/**
 * 只要一句话的请求体，驳回、下架这些都用它
 */
@Data
public class RemarkDTO {

    private String remark;

}
