package com.inkisle.dto;

/** 审核结论。作品与章节两套状态编号不同，传数字容易传错，这里只传原始值 */
public class AuditStatusDTO {

    private Integer status;

    private String remark;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

}
