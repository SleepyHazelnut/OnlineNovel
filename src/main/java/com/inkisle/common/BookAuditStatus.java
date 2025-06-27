package com.inkisle.common;

/**
 * 作品上架状态，存于 book_info.audit_status
 * <p>
 * 与 book_audit_record.status 不是同一套编号：记录表只有 待审0 / 通过1 / 驳回2，
 * 而作品多了「未提交」与「已下架」，数值整体后移。两处不要互相赋值，
 * 流转统一经由本枚举，记录表的状态在 BookAuditServiceImpl 内部单独映射
 * <p>
 * 只有 {@link #PASSED} 对读者可见，其余状态一律从书库、搜索、榜单和详情页消失
 */
public enum BookAuditStatus {

    /** 刚建好，作者还没送审 */
    DRAFT(0, "未提交"),

    /** 已送审，等管理员处理 */
    PENDING(1, "待审核"),

    /** 审核通过，读者可见 */
    PASSED(2, "已通过"),

    /** 被驳回，作者需修改后重新提交 */
    REJECTED(3, "已驳回"),

    /** 过审后被管理员下架，读者不可见 */
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

    /**
     * 仅「已通过」对读者可见。空值按不可见处理：
     * 状态缺失说明数据有问题，此时宁可不上架，也不要漏出未审内容
     */
    public static boolean isVisible(Integer auditStatus) {
        return auditStatus != null && auditStatus == PASSED.code;
    }

    /**
     * 作者可以送审的状态：草稿、驳回后重投、下架后整改
     * 已通过和待审期间不允许重复提交
     */
    public static boolean canSubmit(Integer auditStatus) {
        return auditStatus != null
                && (auditStatus == DRAFT.code || auditStatus == REJECTED.code || auditStatus == OFFLINE.code);
    }

}
