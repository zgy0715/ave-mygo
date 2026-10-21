-- =====================================================================
-- V42：把两场虚构的演示演出替换为「真实演出信息」
--
-- 数据来源（2026-10-21 官方站核实）：
--  1) MyGO!!!!! 9th LIVE「つなぎ目の向こうに」- 神戸再景編 -
--     https://bang-dream.com/events/mygo_9th_hyogo
--     DAY1 2026-11-14(土) 開場16:30 / 開演18:00
--     DAY2 2026-11-15(日) 開場15:30 / 開演17:00
--     会場 ワールド記念ホール（神戸ポートアイランドホール）
--  2) Ave Mujica 7th LIVE「Virtus」
--     https://bang-dream.com/events/avemujica_7th
--     DAY1 2026-10-24(土) 開場17:00 / 開演18:30
--     DAY2 2026-10-25(日) 開場16:00 / 開演17:30
--     会場 京王アリーナ TOKYO
--
-- 说明：名称/场馆/日程/席种为真实信息；票档「金额」沿用本站演示价（人民币），
--       真实日元票价写在 description 里，避免把 ¥8,800 误当人民币展示。
-- =====================================================================

-- ---------- 1. 演出主体：MyGO!!!!! 9th LIVE（神戸再景編） ----------
UPDATE live_event SET
  `title` = 'MyGO!!!!! 9th LIVE「つなぎ目の向こうに」- 神戸再景編 -',
  `venue` = 'ワールド記念ホール（神戸ポートアイランドホール）',
  `status` = 'PUBLISHED',
  `description` = 'MyGO!!!!! 第 9 场专场，神户世界纪念大厅两日公演（2026.11.14-15）。官方票价参考：一般指定席 8,800 円 / 一般指定席（特製グッズ付）13,200 円 / A席（特製グッズ付）17,600 円 / S席（特製グッズ付）25,300 円。本站票档金额为演示数据。'
WHERE `id` = 1;

-- 场次：DAY1 / DAY2（开演时间取自官网）
UPDATE live_session SET
  `name` = 'DAY1（11.14 神戸）',
  `start_time` = '2026-11-14 18:00:00',
  `end_time` = '2026-11-14 20:30:00',
  `sale_start_time` = '2026-10-01 12:00:00',
  `status` = 'ONSALE'
WHERE `id` = 1;

UPDATE live_session SET
  `name` = 'DAY2（11.15 神戸）',
  `start_time` = '2026-11-15 17:00:00',
  `end_time` = '2026-11-15 19:30:00',
  `sale_start_time` = '2026-10-01 12:00:00',
  `status` = 'ONSALE'
WHERE `id` = 2;

-- 票档：按官方席种命名（金额保持演示价）
UPDATE ticket_type SET `name` = 'S席（特製グッズ付）', `price` = 980.00 WHERE `id` = 1;
UPDATE ticket_type SET `name` = '一般指定席',           `price` = 480.00 WHERE `id` = 2;
UPDATE ticket_type SET `name` = 'S席（特製グッズ付）', `price` = 980.00 WHERE `id` = 3;
UPDATE ticket_type SET `name` = '一般指定席',           `price` = 480.00 WHERE `id` = 4;

-- ---------- 2. 演出主体：Ave Mujica 7th LIVE「Virtus」 ----------
UPDATE live_event SET
  `title` = 'Ave Mujica 7th LIVE「Virtus」',
  `venue` = '京王アリーナ TOKYO',
  `status` = 'PUBLISHED',
  `description` = 'Ave Mujica 第 7 场专场，京王 Arena TOKYO 两日公演（2026.10.24-25）。官方票价参考：一般指定席 9,900 円 / A席（特製グッズ付）16,500 円 / S席（特製グッズ付）24,200 円。本站票档金额为演示数据。'
WHERE `id` = 2;

UPDATE live_session SET
  `name` = 'DAY1（10.24 TOKYO）',
  `start_time` = '2026-10-24 18:30:00',
  `end_time` = '2026-10-24 21:00:00',
  `sale_start_time` = '2026-10-01 12:00:00',
  `status` = 'ONSALE'
WHERE `id` = 3;

-- 官方为两日公演：补 DAY2 场次 + 两个票档
INSERT INTO live_session (`id`, `event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`) VALUES
(4, 2, 'DAY2（10.25 TOKYO）', '2026-10-25 17:30:00', '2026-10-25 20:00:00', '2026-10-01 12:00:00', 'ONSALE');

UPDATE ticket_type SET `name` = 'S席（特製グッズ付）', `price` = 880.00 WHERE `id` = 5;
UPDATE ticket_type SET `name` = '一般指定席',           `price` = 380.00 WHERE `id` = 6;

INSERT INTO ticket_type (`id`, `session_id`, `name`, `price`, `total_stock`, `sold_count`, `max_per_user`, `status`) VALUES
(7, 4, 'S席（特製グッズ付）', 880.00, 40,  0, 2, 1),
(8, 4, '一般指定席',         380.00, 120, 0, 4, 1);
