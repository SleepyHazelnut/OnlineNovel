-- ----------------------------
-- 存量库升级：book_info 增加上架状态
--
-- 适用范围：2025-08 之前建好的库。全新导入 schema.sql 的库已含这两列，不必执行
-- 执行一次即可；重复执行会因列已存在而报错，属预期
--
-- 迁移前，作品是否对读者可见取决于「最新一条审核记录」，没有记录的老书视为已上架
-- 迁移时按同一口径回填，保证读者看到的内容在迁移前后完全一致；
-- 之后由代码维护这一列，不再实时查审核记录表
-- ----------------------------

ALTER TABLE `book_info`
    ADD COLUMN `audit_status` tinyint NOT NULL DEFAULT '0'
        COMMENT '上架状态 0未提交 1待审核 2已通过 3已驳回 4已下架' AFTER `status`,
    ADD COLUMN `audit_remark` varchar(255) DEFAULT NULL
        COMMENT '最近一次审核意见，驳回或下架的原因' AFTER `audit_status`,
    ADD KEY `idx_audit_status` (`audit_status`);

-- 回填：取每本书最新一条审核记录，0待审→1、1通过→2、2驳回→3；
-- 没有审核记录的老书按原逻辑视为已上架，置为 2
UPDATE `book_info` b
SET b.audit_status = CASE (
        SELECT r.status
        FROM `book_audit_record` r
        WHERE r.book_id = b.id
        ORDER BY r.id DESC
        LIMIT 1
    )
        WHEN 0 THEN 1
        WHEN 1 THEN 2
        WHEN 2 THEN 3
        ELSE 2
    END;

-- 核对：三列应一一对应，且没有遗漏的行
-- SELECT b.id, b.book_name, b.audit_status,
--        (SELECT r.status FROM book_audit_record r WHERE r.book_id = b.id ORDER BY r.id DESC LIMIT 1) AS latest_record
-- FROM book_info b ORDER BY b.id;
