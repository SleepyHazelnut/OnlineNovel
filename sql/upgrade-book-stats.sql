-- ----------------------------
-- 存量库升级：作品统计字段与评分表
--
-- 原先阅读次数只存在 Redis 的 ZSet 里，进程重启或 key 过期后榜单归零，
-- 作者端也看不到自己作品被读了多少次；评论数与总字数则每次实时聚合
-- 这几项都要参与排序和展示，改为在 book_info 上维护一列
--
-- 本脚本可重复执行：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，
-- 重复执行时会报 Duplicate column，忽略该错误即可
-- ----------------------------
ALTER TABLE `book_info`
    ADD COLUMN `visit_count` int NOT NULL DEFAULT '0' COMMENT '阅读次数，每打开一章累加一次' AFTER `audit_remark`,
    ADD COLUMN `word_count` int NOT NULL DEFAULT '0' COMMENT '总字数，由章节正文字数汇总' AFTER `visit_count`,
    ADD COLUMN `comment_count` int NOT NULL DEFAULT '0' COMMENT '评论数，只统计审核通过的评论' AFTER `word_count`,
    ADD COLUMN `score` decimal(3,1) NOT NULL DEFAULT '0.0' COMMENT '平均评分 0-5，无人评分时为 0' AFTER `comment_count`,
    ADD COLUMN `rating_count` int NOT NULL DEFAULT '0' COMMENT '评分人数' AFTER `score`,
    ADD KEY `idx_visit_count` (`visit_count`),
    ADD KEY `idx_update_time` (`update_time`),
    ADD KEY `idx_create_time` (`create_time`);

ALTER TABLE `book_chapter`
    ADD COLUMN `word_count` int NOT NULL DEFAULT '0' COMMENT '本章字数' AFTER `chapter_num`;

CREATE TABLE IF NOT EXISTS `book_rating` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `user_id` bigint NOT NULL COMMENT '评分人ID',
  `score` tinyint NOT NULL COMMENT '评分 1-5 星',
  `create_time` datetime DEFAULT NULL COMMENT '首次评分时间',
  `update_time` datetime DEFAULT NULL COMMENT '最近一次修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_user` (`book_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍评分表';

-- 回填总字数：正文按章节归属到书，CHAR_LENGTH 统计字符数而非字节数
UPDATE `book_info` b
SET b.`word_count` = (
    SELECT COALESCE(SUM(CHAR_LENGTH(c.`content`)), 0)
    FROM `book_content` c
    JOIN `book_chapter` ch ON ch.`id` = c.`chapter_id`
    WHERE ch.`book_id` = b.`id`
);

-- 回填章节字数
UPDATE `book_chapter` ch
SET ch.`word_count` = (
    SELECT COALESCE(CHAR_LENGTH(c.`content`), 0)
    FROM `book_content` c
    WHERE c.`chapter_id` = ch.`id`
);

-- 回填评论数；阅读次数没有历史数据可回填，从 0 开始累计
UPDATE `book_info` b
SET b.`comment_count` = (
    SELECT COUNT(*) FROM `book_comment` c WHERE c.`book_id` = b.`id`
);
