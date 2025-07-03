package com.inkisle.common;

/**
 * 分页参数兜底。页码没有上限时 (pageNum - 1) * pageSize 会在 int 上溢出成负数，
 * 拼进 LIMIT 之后数据库报语法错误
 */
public final class PageUtils {

    private static final int MIN_PAGE_NUM = 1;

    private static final int MAX_PAGE_NUM = 10000;

    private static final int MAX_PAGE_SIZE = 100;

    private PageUtils() {
    }

    public static int pageNum(Integer pageNum) {
        if (pageNum == null || pageNum < MIN_PAGE_NUM) {
            return MIN_PAGE_NUM;
        }
        return Math.min(pageNum, MAX_PAGE_NUM);
    }

    public static int pageSize(Integer pageSize, int defaultSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultSize;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public static int offset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

}
