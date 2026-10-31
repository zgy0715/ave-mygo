-- V51: Agent 会话消息表（多轮上下文）
-- 目的：运营模式下"把刚才那场删掉""为什么没删掉"这类追问需要历史上下文。
-- 与 agent_tool_calls 分工：本表存人机对话文本，审计表存工具调用明细（含参数与结果）。

CREATE TABLE IF NOT EXISTS `agent_messages` (
  `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `conversation_id` VARCHAR(64)     NOT NULL COMMENT '会话标识（前端小灯面板生成，整页复用）',
  `user_id`         BIGINT UNSIGNED NOT NULL COMMENT '发起人（ADMIN）',
  `role`            VARCHAR(16)     NOT NULL COMMENT 'USER / ASSISTANT',
  `content`         TEXT            NOT NULL COMMENT '消息文本（工具结果不回填，仅存人机对话）',
  `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_msg_conv` (`conversation_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Agent 会话消息（多轮上下文来源，按 conversation_id 取最近 N 条）';
