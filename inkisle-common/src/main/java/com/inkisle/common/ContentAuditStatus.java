package com.inkisle.common;

/**
 * 章节和评论的审核状态，就三态。编号跟 BookAuditStatus 不一样，别混着用
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
