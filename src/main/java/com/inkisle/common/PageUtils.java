package com.inkisle.common;

/**
 * 分页参数归一化
 * <p>
 * 分页参数直接来自查询串，不设上限时前端传 {@code pageSize=10000} 就能一次拖走整张表；
 * 页码同样要挡：{@code (pageNum - 1) * pageSize} 在 int 上会溢出成负数，
 * 拼进 LIMIT 后数据库直接报语法错误
 * <p>
 * 上限取 100 条，够一页展示，也不至于让单次查询失去意义
 */
public final class PageUtils {

    /** 页码下限 */
    private static final int MIN_PAGE_NUM = 1;

    /** 页码上限：再往后翻也没有内容，且留出余量避免 offset 溢出 */
    private static final int MAX_PAGE_NUM = 10000;

    /** 每页条数上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private PageUtils() {
    }

    /**
     * @param pageNum 前端传来的页码，可为空
     * @return 落在 [1, 10000] 内的页码
     */
    public static int pageNum(Integer pageNum) {
        if (pageNum == null || pageNum < MIN_PAGE_NUM) {
            return MIN_PAGE_NUM;
        }
        return Math.min(pageNum, MAX_PAGE_NUM);
    }

    /**
     * @param pageSize 前端传来的每页条数，可为空
     * @param defaultSize 为空时采用的默认值
     * @return 落在 [1, 100] 内的条数
     */
    public static int pageSize(Integer pageSize, int defaultSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultSize;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 页码与条数都已归一化，乘积最大一百零几万，不会溢出 int
     */
    public static int offset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }

}
