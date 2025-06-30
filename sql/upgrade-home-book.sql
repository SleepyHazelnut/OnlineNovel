-- ----------------------------
-- 存量库升级：首页推荐位
--
-- 原先首页直接就是书库列表，没有可配置的推荐区
-- 推荐位只存书籍 id 与板块，书名封面仍从 book_info 读，
-- 避免同一本书的信息存两份。本脚本可重复执行，表已存在时跳过
-- ----------------------------
CREATE TABLE IF NOT EXISTS `home_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `section` tinyint NOT NULL COMMENT '板块 1热门推荐 2精品推荐',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序，越大越靠前',
  `create_time` datetime DEFAULT NULL COMMENT '加入推荐位的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_section_book` (`section`, `book_id`),
  KEY `idx_section_sort` (`section`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页推荐位';
