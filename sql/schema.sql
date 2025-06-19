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
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author_id` (`author_id`),
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
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_book_id_chapter_num` (`book_id`, `chapter_num`)
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
