package com.inkisle.common;

/**
 * 章节与评论的审核状态，三态。与 BookAuditStatus 编号不同，别混用
 */
public enum ContentAuditStatus {

    PENDING(0),

    PASSED(1),

    REJECTED(2);

    private final int code;

    ContentAuditStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isVisible(Integer code) {
        return code != null && code == PASSED.code;
    }

    public static boolean isValid(Integer code) {
        return code != null && (code == PENDING.code || code == PASSED.code || code == REJECTED.code);
    }

}
