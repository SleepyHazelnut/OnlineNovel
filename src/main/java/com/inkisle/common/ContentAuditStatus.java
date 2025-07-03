package com.inkisle.common;

/**
 * 章节与评论的审核状态
 * <p>
 * 只有三态：待审、通过、驳回。与 {@link BookAuditStatus} 不是一套：
 * 作品还有「未提交」「已下架」两个作者侧动作，编号与含义都不同，两者不要互相赋值
 * 审核记录表（book_audit_record）的三个编号与本枚举一致，但那张表只记作品送审
 */
public enum ContentAuditStatus {

    /** 待审核，只有作者本人与管理端能看到 */
    PENDING(0),

    /** 审核通过，读者端可见 */
    PASSED(1),

    /** 审核驳回，读者端不可见，作者可修改后重新进入待审 */
    REJECTED(2);

    private final int code;

    ContentAuditStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 只有审核通过的章节与评论对读者可见
     */
    public static boolean isVisible(Integer code) {
        return code != null && code == PASSED.code;
    }

    /**
     * 状态是否合法，接口入参用它兜住 0/1/2 之外的值
     */
    public static boolean isValid(Integer code) {
        return code != null && (code == PENDING.code || code == PASSED.code || code == REJECTED.code);
    }

}
