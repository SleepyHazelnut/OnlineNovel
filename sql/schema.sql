SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `inkisle` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `inkisle`;

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
  `status` tinyint DEFAULT '0' COMMENT '状态 0正常 1封禁',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- ----------------------------
-- 作者信息表
-- 读者和作者分开注册，账号体系互相独立
-- ----------------------------
DROP TABLE IF EXISTS `author_info`;
CREATE TABLE `author_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '作者账号',
  `password` varchar(64) NOT NULL COMMENT '密码(MD5)',
  `pen_name` varchar(50) DEFAULT NULL COMMENT '笔名',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `intro` varchar(255) DEFAULT NULL COMMENT '作者简介',
  `audit_status` tinyint DEFAULT '0' COMMENT '资质审核 0待审 1通过 2驳回',
  `audit_remark` varchar(255) DEFAULT NULL COMMENT '资质审核意见',
  `status` tinyint DEFAULT '0' COMMENT '状态 0正常 1禁用',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作者信息表';

-- ----------------------------
-- 管理员表
-- 不开放注册，账号由运维直接往库里加
-- ----------------------------
DROP TABLE IF EXISTS `admin_info`;
CREATE TABLE `admin_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '管理员账号',
  `password` varchar(64) NOT NULL COMMENT '密码(MD5)',
  `real_name` varchar(50) DEFAULT NULL COMMENT '姓名',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- ----------------------------
-- 书籍审核记录表
-- 作者每次提交审核写一条，管理员审完改这条的状态
-- ----------------------------
DROP TABLE IF EXISTS `book_audit_record`;
CREATE TABLE `book_audit_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `author_id` bigint NOT NULL COMMENT '提交的作者ID',
  `status` tinyint DEFAULT '0' COMMENT '审核状态 0待审 1通过 2驳回',
  `remark` varchar(255) DEFAULT NULL COMMENT '审核意见',
  `audit_admin_id` bigint DEFAULT NULL COMMENT '审核人ID',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  PRIMARY KEY (`id`),
  KEY `idx_book_id` (`book_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍审核记录表';

-- ----------------------------
-- 书籍信息表
-- ----------------------------
DROP TABLE IF EXISTS `book_info`;
CREATE TABLE `book_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_name` varchar(100) NOT NULL COMMENT '书名',
  `author_name` varchar(50) DEFAULT NULL COMMENT '作者名',
  `author_id` bigint DEFAULT NULL COMMENT '作者ID，老数据没有作者账号所以允许为空',
  `category_id` bigint DEFAULT NULL COMMENT '分类ID',
  `book_desc` varchar(500) DEFAULT NULL COMMENT '简介',
  `cover_url` varchar(255) DEFAULT NULL COMMENT '封面地址',
  `status` tinyint DEFAULT '0' COMMENT '状态 0连载 1完结',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '上架状态 0未提交 1待审核 2已通过 3已驳回 4已下架',
  `audit_remark` varchar(255) DEFAULT NULL COMMENT '最近一次审核意见，驳回或下架的原因',
  `visit_count` int NOT NULL DEFAULT '0' COMMENT '阅读次数，每打开一章累加一次',
  `word_count` int NOT NULL DEFAULT '0' COMMENT '总字数，由章节正文字数汇总',
  `comment_count` int NOT NULL DEFAULT '0' COMMENT '评论数，只统计审核通过的评论',
  `score` decimal(3,1) NOT NULL DEFAULT '0.0' COMMENT '平均评分 0-5，无人评分时为 0',
  `rating_count` int NOT NULL DEFAULT '0' COMMENT '评分人数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_audit_status` (`audit_status`),
  -- 点击榜按 visit_count 排序，新书榜按 create_time，更新榜按 update_time，
  -- 三个榜单都只取前 N 名，索引让排序不必全表扫
  KEY `idx_visit_count` (`visit_count`),
  KEY `idx_update_time` (`update_time`),
  KEY `idx_create_time` (`create_time`),
  FULLTEXT KEY `ft_book_search` (`book_name`, `author_name`) WITH PARSER ngram
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
-- 只放目录信息，正文拆到 book_content
-- ----------------------------
DROP TABLE IF EXISTS `book_chapter`;
CREATE TABLE `book_chapter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `chapter_name` varchar(200) DEFAULT NULL COMMENT '章节名',
  `chapter_num` int DEFAULT NULL COMMENT '章节序号',
  `word_count` int NOT NULL DEFAULT '0' COMMENT '本章字数',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_id_chapter_num` (`book_id`, `chapter_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节表';

-- ----------------------------
-- 章节内容表
-- 正文单独存一张表，目录查询就不用把大字段一起拽出来了
-- ----------------------------
DROP TABLE IF EXISTS `book_content`;
CREATE TABLE `book_content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `chapter_id` bigint NOT NULL COMMENT '章节ID',
  `content` longtext COMMENT '章节正文',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_id` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='章节内容表';

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

-- ----------------------------
-- 书籍评论表
-- ----------------------------
DROP TABLE IF EXISTS `book_comment`;
CREATE TABLE `book_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `user_id` bigint NOT NULL COMMENT '评论人ID',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `parent_id` bigint DEFAULT '0' COMMENT '父评论ID，0 表示一级评论',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍评论表';

-- ----------------------------
-- 书籍评分表
-- 一位读者对一本书只留一条评分，重复打分覆盖旧值，故有 (book_id, user_id) 唯一键
-- ----------------------------
DROP TABLE IF EXISTS `book_rating`;
CREATE TABLE `book_rating` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `user_id` bigint NOT NULL COMMENT '评分人ID',
  `score` tinyint NOT NULL COMMENT '评分 1-5 星',
  `create_time` datetime DEFAULT NULL COMMENT '首次评分时间',
  `update_time` datetime DEFAULT NULL COMMENT '最近一次修改时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_user` (`book_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书籍评分表';

-- ----------------------------
-- 首页推荐位
-- 只记录「哪本书放在哪个板块」，书名封面等仍从 book_info 读，
-- 避免同一本书的信息存两份、改了简介推荐位还显示旧内容
-- section：1 热门推荐，2 精品推荐；最新更新不占推荐位，按更新时间实时取
-- ----------------------------
DROP TABLE IF EXISTS `home_book`;
CREATE TABLE `home_book` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `section` tinyint NOT NULL COMMENT '板块 1热门推荐 2精品推荐',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序，越大越靠前',
  `create_time` datetime DEFAULT NULL COMMENT '加入推荐位的时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_section_book` (`section`, `book_id`),
  KEY `idx_section_sort` (`section`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页推荐位';

-- ----------------------------
-- 书架收藏表
-- ----------------------------
DROP TABLE IF EXISTS `user_bookshelf`;
CREATE TABLE `user_bookshelf` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `book_id` bigint NOT NULL COMMENT '书籍ID',
  `create_time` datetime DEFAULT NULL COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_book` (`user_id`, `book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='书架收藏表';

-- ----------------------------
-- 初始化数据
-- 分类与账号是系统能跑起来的前提：没有分类，作者新建作品时无处可选；
-- 没有管理员，后台登录页无人可登。故随建表脚本一并写入，空库导入后即可使用
-- 账号与 README 的演示账号一致，密码均为 123456，管理员为 admin123
-- 正式部署前请修改密码，或直接删除这几条演示数据
-- ----------------------------
INSERT INTO `book_category` (`id`, `category_name`, `parent_id`, `sort`) VALUES
(1, '玄幻', 0, 1),
(2, '都市', 0, 2),
(3, '历史', 0, 3),
(4, '科幻', 0, 4),
(5, '东方玄幻', 1, 1),
(6, '异世大陆', 1, 2),
(7, '都市异能', 2, 1);

INSERT INTO `user_info` (`id`, `username`, `password`, `nick_name`, `status`, `create_time`, `update_time`) VALUES
(1, 'reader1', 'e10adc3949ba59abbe56e057f20f883e', 'reader1', 0, NOW(), NOW());

-- 演示作者资质直接置为通过，否则登录作者端后无法发布作品
INSERT INTO `author_info` (`id`, `username`, `password`, `pen_name`, `intro`, `audit_status`, `status`, `create_time`, `update_time`) VALUES
(1, 'bimo', 'e10adc3949ba59abbe56e057f20f883e', '白墨', '演示作者账号', 1, 0, NOW(), NOW());

INSERT INTO `admin_info` (`id`, `username`, `password`, `real_name`, `create_time`, `update_time`) VALUES
(1, 'admin', '0192023a7bbd73250516f069df18b500', '超级管理员', NOW(), NOW());
