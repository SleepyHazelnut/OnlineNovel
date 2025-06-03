SET NAMES utf8mb4;

-- ----------------------------
-- 用户信息表
-- ----------------------------
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(64) NOT NULL COMMENT '密码(MD5)',
  `nick_name` varchar(50) DEFAULT NULL COMMENT '昵称',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- ----------------------------
-- 书籍信息表
-- ----------------------------
DROP TABLE IF EXISTS `book_info`;
CREATE TABLE `book_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_name` varchar(100) NOT NULL COMMENT '书名',
  `author_name` varchar(50) DEFAULT NULL COMMENT '作者名',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `book_desc` varchar(500) DEFAULT NULL COMMENT '简介',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面地址',
  `status` tinyint DEFAULT '0' COMMENT '状态 0连载 1完结',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍信息表';

-- ----------------------------
-- 书籍分类表
-- ----------------------------
DROP TABLE IF EXISTS `book_category`;
CREATE TABLE `book_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `category_name` varchar(50) DEFAULT NULL COMMENT '分类名',
  `parent_id` bigint DEFAULT '0' COMMENT '父级分类ID，0 表示一级分类',
  `sort` int DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍分类表';

-- ----------------------------
-- 章节表
-- ----------------------------
DROP TABLE IF EXISTS `book_chapter`;
CREATE TABLE `book_chapter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `chapter_name` varchar(200) DEFAULT NULL COMMENT '章节名',
  `chapter_num` int DEFAULT NULL COMMENT '章节序号',
  `content` longtext COMMENT '章节正文',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节表';

-- ----------------------------
-- 阅读记录表
-- ----------------------------
DROP TABLE IF EXISTS `user_read_history`;
CREATE TABLE `user_read_history` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `chapter_id` bigint NOT NULL COMMENT '章节ID',
  `chapter_num` int DEFAULT NULL COMMENT '章节序号',
  `update_time` datetime DEFAULT NULL COMMENT '最近阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_book` (`user_id`, `book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阅读记录表';
