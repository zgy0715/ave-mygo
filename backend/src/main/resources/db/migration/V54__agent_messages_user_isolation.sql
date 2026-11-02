-- V54: 助手会话历史按用户隔离（审阅项 1）
--
-- 原索引只有 (conversation_id, id)，查询也只按 conversation_id 过滤。
-- conversationId 由前端生成，理论上拿到别人的 id 就能把对方历史读进自己的对话。
-- 修复后查询条件改为 user_id + conversation_id，索引随之调整。

CREATE INDEX `idx_agent_msg_user_conv`
    ON `agent_messages` (`user_id`, `conversation_id`, `id`);
