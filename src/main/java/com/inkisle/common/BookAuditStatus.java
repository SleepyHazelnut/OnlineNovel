package com.inkisle.common;

/**
 * 作品上架状态，存 book_info.audit_status
 * 与 book_audit_record.status 不是一套编号（那边只有 0 待审 / 1 通过 / 2 驳回），
 * 两边不要互相赋值
 */
public enum BookAuditStatus {

    DRAFT(0, "未提交"),

    PENDING(1, "待审核"),

    PASSED(2, "已通过"),

    REJECTED(3, "已驳回"),

    OFFLINE(4, "已下架");

    private final int code;

    private final String text;

    BookAuditStatus(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public int getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    /** 空值按不可见处理，宁可不上架也不要漏出未审内容 */
    public static boolean isVisible(Integer auditStatus) {
        return auditStatus != null && auditStatus == PASSED.code;
    }

    /** 草稿、驳回后重投、下架后整改都可以再提交 */
    public static boolean canSubmit(Integer auditStatus) {
        return auditStatus != null
                && (auditStatus == DRAFT.code || auditStatus == REJECTED.code || auditStatus == OFFLINE.code);
    }

}
