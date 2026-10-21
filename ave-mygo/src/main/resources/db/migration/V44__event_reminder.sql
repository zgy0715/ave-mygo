-- =====================================================================
-- V44__event_reminder.sql —— 开售提醒订阅
-- 一个用户对一场演出只能订阅一次（唯一键）；notified 由定时任务置 1
-- =====================================================================
CREATE TABLE IF NOT EXISTS `event_reminder` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL,
    `event_id`    BIGINT UNSIGNED NOT NULL,
    `notified`    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已提醒',
    `notified_at` DATETIME        NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_event` (`user_id`, `event_id`),
    KEY `idx_pending` (`notified`, `event_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='开售提醒订阅';
