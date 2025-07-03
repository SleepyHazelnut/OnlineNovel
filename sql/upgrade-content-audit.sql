-- ----------------------------
-- 存量库升级：章节与评论审核
--
-- 原先只有作品送审，通过后整本书的章节直接可见，评论发出来就公开展示
-- 章节正文与评论才是违规内容最容易出现的地方，这里补上两者的审核状态
--
-- 存量数据一律置为「通过」：它们已经在线展示过，若按默认值落成待审，
-- 升级后全书章节与评论会从读者端一次性消失
--
-- 只执行一次。末尾两条 UPDATE 会把所有待审内容置为通过，
-- 上线后重跑等于把新提交的稿件一键放行；ADD COLUMN 会在重跑时报错中断，
-- 但若只挑 UPDATE 单独执行则没有这层保护
-- ----------------------------
ALTER TABLE `book_chapter`
    ADD COLUMN `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态 0待审 1通过 2驳回' AFTER `word_count`,
    ADD COLUMN `reject_reason` varchar(200) DEFAULT NULL COMMENT '驳回原因' AFTER `audit_status`,
    ADD KEY `idx_audit_status` (`audit_status`);

ALTER TABLE `book_comment`
    ADD COLUMN `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态 0待审 1通过 2驳回' AFTER `parent_id`,
    ADD COLUMN `reject_reason` varchar(200) DEFAULT NULL COMMENT '驳回原因' AFTER `audit_status`,
    ADD KEY `idx_audit_status` (`audit_status`);

UPDATE `book_chapter` SET `audit_status` = 1 WHERE `audit_status` = 0;
UPDATE `book_comment` SET `audit_status` = 1 WHERE `audit_status` = 0;

-- 评论数口径改为只统计审核通过的评论
UPDATE `book_info` b
SET b.`comment_count` = (
    SELECT COUNT(*) FROM `book_comment` c
    WHERE c.`book_id` = b.`id` AND c.`audit_status` = 1
);
