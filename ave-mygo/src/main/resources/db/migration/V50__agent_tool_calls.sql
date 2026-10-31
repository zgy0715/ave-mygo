-- V50: Agent 运营层审计表（小灯 v2，见 docs/10-Agent运营层设计.md §7）
-- 幂等：IF NOT EXISTS，允许重放

CREATE TABLE IF NOT EXISTS `agent_tool_calls` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` VARCHAR(64)     NOT NULL COMMENT '会话标识（前端生成，用于聚合一次任务的多次调用）',
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '发起人（ADMIN）',
  `tool_name`       VARCHAR(64)     NOT NULL COMMENT '工具名',
  `risk`            VARCHAR(8)      NOT NULL COMMENT 'READ / WRITE',
  `arguments_json`  TEXT            NOT NULL COMMENT 'LLM 给出的参数原文',
  `result`          TEXT            NULL COMMENT '执行结果或错误信息（截断 2000 字符）',
  `status`          VARCHAR(16)     NOT NULL COMMENT 'PENDING/EXECUTED/REJECTED/FAILED/EXPIRED',
  `token`           VARCHAR(36)     NULL COMMENT 'WRITE 操作的确认令牌',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_conv` (`conversation_id`),
  KEY `idx_agent_token` (`token`),
  KEY `idx_agent_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 工具调用审计（只增不删，出问题可回溯）';
