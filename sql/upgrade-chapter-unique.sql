-- ----------------------------
-- 存量库升级：章节号在「同一本书内」唯一
--
-- 原先只有普通索引 idx_book_id_chapter_num，而追加章节是「查最大值 + 1 再插入」，
-- 两个并发请求会读到同一个最大值，写出两个相同的章节号
-- 代码侧已改为先锁作品行再取号；这里补上数据库兜底，唯一索引同时能服务
-- 原先按 book_id 的查询（最左前缀），故直接替换掉旧索引
--
-- 库里有重号时下面的 ALTER 会失败，先查出来再决定怎么处理：
--   SELECT book_id, chapter_num, COUNT(*) c
--   FROM book_chapter GROUP BY book_id, chapter_num HAVING c > 1;
-- ----------------------------
ALTER TABLE `book_chapter`
    DROP INDEX `idx_book_id_chapter_num`,
    ADD UNIQUE KEY `uk_book_id_chapter_num` (`book_id`, `chapter_num`);
