-- =====================================================================
-- V23__conversation_history.sql —— AI 助手对话历史（docs/05 §3.13，最后一张闲置表激活）
-- user_id 可空：游客也能问；session_id 由前端生成，串联一轮多轮对话。
-- =====================================================================

CREATE TABLE `conversation_history` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`    BIGINT UNSIGNED NULL COMMENT '逻辑FK->user，游客为 NULL',
    `session_id` VARCHAR(64)     NOT NULL COMMENT '前端生成的会话标识（uuid）',
    `role`       VARCHAR(20)     NOT NULL COMMENT 'USER / ASSISTANT',
    `content`    TEXT            NOT NULL,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 助手对话历史';
