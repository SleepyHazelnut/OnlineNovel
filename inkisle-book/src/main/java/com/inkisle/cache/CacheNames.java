package com.inkisle.cache;

/**
 * 缓存区域名与 key 前缀
 * 区域名要和 CacheConfig 里配 TTL 的那几个对上，改名字两边一起改，否则会掉到默认 TTL
 * key 前缀收在这里，免得调用处各写各的字符串，漏一个就是命中不了
 */
public final class CacheNames {

    /** 书籍详情，BookServiceImpl.getById 上的 @Cacheable 用的就是它 */
    public static final String BOOK = "book";

    /** 读者端目录，只有已过审的章节 */
    public static final String CHAPTER_CATALOG = "chapterCatalog";

    /** 章节正文，单独一个区域，TTL 给得比目录长 */
    public static final String CHAPTER_CONTENT = "chapterContent";

    private CacheNames() {
    }

    public static String catalogKey(Long bookId) {
        return "b:" + bookId;
    }

    public static String contentKey(Long chapterId) {
        return "c:" + chapterId;
    }

}
