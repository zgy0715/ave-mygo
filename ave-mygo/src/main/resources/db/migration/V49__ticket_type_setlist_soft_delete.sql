-- V49: 管理端三级删除前置 —— ticket_type / setlist_item 补软删列
-- 演出/场次删除时级联软删票档与曲目单；两表原无 is_deleted，先补列。
-- 幂等：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，用 INFORMATION_SCHEMA 守卫，
-- 已存在的列（如手工应用过的环境）跳过，保证 Flyway 重放不报 1060。

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ticket_type` ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''软删标记：0 正常 / 1 已删（场次级联删除时置 1）'' AFTER `status`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_type' AND COLUMN_NAME = 'is_deleted');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `setlist_item` ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT ''软删标记：0 正常 / 1 已删（场次级联删除时置 1）'' AFTER `is_encore`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'setlist_item' AND COLUMN_NAME = 'is_deleted');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
