package com.inkisle.service.support;

/**
 * 等级按「累计获得」算，不按余额
 *
 * 按余额算会有一个副作用：花掉分就掉级，用户于是不敢花，积分变成只进不出的死数。
 * 累计值只增不减，花了分等级还在，这才是激励消费而不是惩罚消费
 */
public enum PointLevel {

    LV1(1, "初读", 0),
    LV2(2, "常读", 60),
    LV3(3, "书友", 200),
    LV4(4, "书痴", 600),
    LV5(5, "藏书家", 1500);

    private final int level;

    private final String name;

    private final int threshold;

    PointLevel(int level, String name, int threshold) {
        this.level = level;
        this.name = name;
        this.threshold = threshold;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    /** 达到这一级所需的累计值 */
    public int getThreshold() {
        return threshold;
    }

    /** 下一级的门槛，已满级返回 null */
    public Integer getNextThreshold() {
        PointLevel next = next();
        return next == null ? null : next.threshold;
    }

    public PointLevel next() {
        PointLevel[] all = values();
        int index = ordinal() + 1;
        return index < all.length ? all[index] : null;
    }

    /** 累计值落到哪一级；负数和空值都按最低级处理 */
    public static PointLevel of(Integer totalEarned) {
        int earned = totalEarned == null ? 0 : totalEarned;
        PointLevel current = LV1;
        for (PointLevel level : values()) {
            if (earned >= level.threshold) {
                current = level;
            }
        }
        return current;
    }

    public static int count() {
        return values().length;
    }

}
