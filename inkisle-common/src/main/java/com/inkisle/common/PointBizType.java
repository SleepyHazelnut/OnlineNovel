package com.inkisle.common;

/**
 * 积分来源，存 user_point_ledger.biz_type
 *
 * 规则写死在枚举里而不是配置中心：分值就六个，改一次要发版，代价远低于
 * 引入配置热更新后「线上和代码里算的不是一回事」的排查成本。
 * 真到了要运营随手调价的地步再挪也不迟，那时候也才有必要做灰度
 *
 * 每个类型带三个约束，加分时统一在这里查，调用方不用各自判一遍：
 * - points：默认分值，为负的是消耗型
 * - dailyCap：每天最多记几条，0 表示不限
 * - minAccountHours：账号注册满多少小时才计分，0 表示立刻生效
 */
public enum PointBizType {

    /** 每天第一次签到，一人一天一条 */
    SIGN("SIGN", "每日签到", 2, 1, 0),

    /**
     * 连签里程碑，分值由调用方按天数算（连签天数不是常量，放不进枚举）
     * 不设每日上限：一天只可能达成一个里程碑
     */
    SIGN_STREAK("SIGN_STREAK", "连续签到奖励", 0, 0, 0),

    /**
     * 读完一章。biz_id 是章节 ID，同一章一辈子只算一次，
     * 所以重复翻同一章不会重复给分——这也是不用 Redis 缓冲的原因：
     * 去重维度要能落库对账，缓冲一旦丢了就是无限刷
     */
    READ_CHAPTER("READ_CHAPTER", "读完一章", 1, 20, 24),

    /** 评分，biz_id 是书籍 ID，一本书只算第一次打分 */
    RATING("RATING", "评了个分", 3, 5, 24),

    /** 评论过审，biz_id 是评论 ID，过审才算，待审和驳回都不给 */
    COMMENT_PASS("COMMENT_PASS", "评论过审", 5, 3, 24),

    /** 消耗：给一本书投荐书票，扣 20 分 */
    RECOMMEND_VOTE("RECOMMEND_VOTE", "荐书票", -20, 6, 24);

    private final String code;

    private final String label;

    private final int points;

    private final int dailyCap;

    private final int minAccountHours;

    PointBizType(String code, String label, int points, int dailyCap, int minAccountHours) {
        this.code = code;
        this.label = label;
        this.points = points;
        this.dailyCap = dailyCap;
        this.minAccountHours = minAccountHours;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getPoints() {
        return points;
    }

    public int getDailyCap() {
        return dailyCap;
    }

    public int getMinAccountHours() {
        return minAccountHours;
    }

    /** 消耗型（花分而不是赚分） */
    public boolean isCost() {
        return points < 0;
    }

    /**
     * 按 code 还原。调用方传的是字符串（跨服务走 Feign，枚举会被序列化成一坨），
     * 认不出来当成参数错误抛，不要静默忽略——静默忽略等于这个来源永远加不上分，
     * 而且没人会发现
     */
    public static PointBizType of(String code) {
        for (PointBizType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new BusinessException("未知的积分来源：" + code);
    }

}
