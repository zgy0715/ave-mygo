-- V53: 订单并发一致性加固（docs/README 审阅项 2）
--
-- 背景：扣库存本身是原子的（ticket_types.sold_count 条件 UPDATE），
-- 但两处是「先查后写」，并发下会破：
--   ① 限购：SELECT SUM(quantity) 再判 → 同一用户并发下单可突破 max_per_user
--   ② 状态流转：读 status → 改对象 → save → 支付与取消并发会互相覆盖，
--      导致库存被回滚两次（真超卖）、或已退款订单被改回已支付
--
-- 本迁移只加结构，逻辑改动在 OrderService / AdminOrderService：
--   1) 限购改为「原子配额」：user_session_quota 一行代表「某用户在某场次已占用张数」，
--      预订用条件 UPDATE（qty + :n <= :max）一次完成检查+占用
--   2) 状态流转改为条件 UPDATE（WHERE status='PENDING_PAYMENT'），按影响行数判定谁抢到

CREATE TABLE IF NOT EXISTS `user_session_quota` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `session_id` BIGINT UNSIGNED NOT NULL,
  `qty`        INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已占用张数（待支付+已支付，取消/退款/超时会释放）',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quota_user_session` (`user_id`, `session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='限购配额：一行 = 某用户在某场次已占用的张数，供原子预订使用';

-- 回填：把现有活跃订单（待支付/已支付）折算成配额，避免上线后限购判断凭空从 0 开始
INSERT INTO `user_session_quota` (`user_id`, `session_id`, `qty`)
SELECT o.`user_id`, o.`session_id`, SUM(o.`quantity`)
FROM `ticket_order` o
WHERE o.`status` IN ('PENDING_PAYMENT', 'PAID')
GROUP BY o.`user_id`, o.`session_id`
ON DUPLICATE KEY UPDATE `qty` = VALUES(`qty`);

-- 超时扫描索引：WHERE status='PENDING_PAYMENT' AND pay_deadline < now
CREATE INDEX `idx_order_status_deadline` ON `ticket_order` (`status`, `pay_deadline`);

-- 限额查询：按场次统计已售（管理端/演出详情用）
CREATE INDEX `idx_order_session_status` ON `ticket_order` (`session_id`, `status`);
