-- V49: 管理端三级删除前置 —— ticket_type / setlist_item 补软删列
-- 演出/场次删除时级联软删票档与曲目单；两表原无 is_deleted，先补列。

ALTER TABLE `ticket_type`
  ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0
    COMMENT '软删标记：0 正常 / 1 已删（场次级联删除时置 1）' AFTER `status`;

ALTER TABLE `setlist_item`
  ADD COLUMN `is_deleted` TINYINT NOT NULL DEFAULT 0
    COMMENT '软删标记：0 正常 / 1 已删（场次级联删除时置 1）' AFTER `is_encore`;
