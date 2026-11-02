-- V52: 补充官方真实演出（数据来源见 docs/14-演出数据补充清单.md）
-- 原则：
--   1) 只用官方 bang-dream.com events 页核验过的场次，查不到的不写
--   2) 全部幂等：按 title / (event_id, session name) 判重，可重放
--   3) 历史场次 status=ENDED，sold_count=0（不参与售票，避免污染在售数据）
--   4) 票价：官方公布的四档照抄；未取到官方价的用 8800 占位并在 description 注明"参考价"
--   5) 海报复用现有乐队素材，不使用未授权视觉图

-- ============ 1. MyGO!!!!! 9th LIVE「つなぎ目の向こうに」横浜公演（2026-07-18/19 ぴあアリーナMM） ============
INSERT INTO `live_event` (`title`, `band_id`, `description`, `poster_url`, `venue`, `status`)
SELECT 'MyGO!!!!! 9th LIVE「つなぎ目の向こうに」〈横浜公演〉', 1,
       '2026年7月18日・19日 ぴあアリーナMM。单独公演两日，票面含特典档位（S/A/一般指定）。官方票价：S席 24,200 / A席 16,500 / 一般指定席（附特典）13,200 / 一般指定席 8,800（含税）。开场嘉宾 DAY1 millsage、DAY2 一家Dumb Rock!。本站已有的是神户追加公演，此为横滨主办公演。',
       '/img/band-mygo.jpg', 'ぴあアリーナMM', 'ENDED'
WHERE NOT EXISTS (SELECT 1 FROM `live_event` WHERE `title` = 'MyGO!!!!! 9th LIVE「つなぎ目の向こうに」〈横浜公演〉');

INSERT INTO `live_session` (`event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`)
SELECT e.id, 'DAY1（7.18 横浜）', '2026-07-18 18:00:00', '2026-07-18 20:30:00', '2026-05-01 12:00:00', 'ENDED'
FROM `live_event` e
WHERE e.`title` = 'MyGO!!!!! 9th LIVE「つなぎ目の向こうに」〈横浜公演〉'
  AND NOT EXISTS (SELECT 1 FROM `live_session` s WHERE s.`event_id` = e.id AND s.`name` = 'DAY1（7.18 横浜）');

INSERT INTO `live_session` (`event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`)
SELECT e.id, 'DAY2（7.19 横浜）', '2026-07-19 18:00:00', '2026-07-19 20:30:00', '2026-05-01 12:00:00', 'ENDED'
FROM `live_event` e
WHERE e.`title` = 'MyGO!!!!! 9th LIVE「つなぎ目の向こうに」〈横浜公演〉'
  AND NOT EXISTS (SELECT 1 FROM `live_session` s WHERE s.`event_id` = e.id AND s.`name` = 'DAY2（7.19 横浜）');

-- 横滨公演四档票价（官方价）
INSERT INTO `ticket_type` (`session_id`, `name`, `price`, `total_stock`, `sold_count`, `max_per_user`, `status`)
SELECT s.id, t.name, t.price, 100, 0, 2, 1
FROM `live_session` s
JOIN `live_event` e ON e.id = s.`event_id`
JOIN (SELECT 'S席（附特典）' AS name, 24200.00 AS price
      UNION ALL SELECT 'A席（附特典）', 16500.00
      UNION ALL SELECT '一般指定席（附特典）', 13200.00
      UNION ALL SELECT '一般指定席', 8800.00) t
WHERE e.`title` = 'MyGO!!!!! 9th LIVE「つなぎ目の向こうに」〈横浜公演〉'
  AND s.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ticket_type` tt WHERE tt.`session_id` = s.id AND tt.`name` = t.name AND tt.`is_deleted` = 0);

-- ============ 2. BanG Dream! Special LIVE in TAIPEI DAY1（MyGO!!!!!×Ave Mujica「"moment / memory"」，2026-04-11 台北・大佳河濱公園） ============
INSERT INTO `live_event` (`title`, `band_id`, `description`, `poster_url`, `venue`, `status`)
SELECT 'BanG Dream! Special LIVE in TAIPEI DAY1「moment / memory」', 2,
       '2026年4月11日 台北・大佳河濱公園。MyGO!!!!! × Ave Mujica 双乐队拼盘公演（DAY1）。票价档位未从官方页取到，站内以 8,800 参考价占位，实际以官方公告为准；本站按 Ave Mujica 归档。',
       '/img/band-mujica.png', '台北・大佳河濱公園', 'ENDED'
WHERE NOT EXISTS (SELECT 1 FROM `live_event` WHERE `title` = 'BanG Dream! Special LIVE in TAIPEI DAY1「moment / memory」');

INSERT INTO `live_session` (`event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`)
SELECT e.id, 'DAY1（4.11 台北）', '2026-04-11 18:00:00', '2026-04-11 21:00:00', '2026-02-01 12:00:00', 'ENDED'
FROM `live_event` e
WHERE e.`title` = 'BanG Dream! Special LIVE in TAIPEI DAY1「moment / memory」'
  AND NOT EXISTS (SELECT 1 FROM `live_session` s WHERE s.`event_id` = e.id AND s.`name` = 'DAY1（4.11 台北）');

INSERT INTO `ticket_type` (`session_id`, `name`, `price`, `total_stock`, `sold_count`, `max_per_user`, `status`)
SELECT s.id, t.name, t.price, 100, 0, 2, 1
FROM `live_session` s
JOIN `live_event` e ON e.id = s.`event_id`
JOIN (SELECT '一般指定席（参考价）' AS name, 8800.00 AS price) t
WHERE e.`title` = 'BanG Dream! Special LIVE in TAIPEI DAY1「moment / memory」'
  AND s.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ticket_type` tt WHERE tt.`session_id` = s.id AND tt.`name` = t.name AND tt.`is_deleted` = 0);

-- ============ 3. Ave Mujica LIVE TOUR 2026「Exitus」（8 场：福冈/难波/名古屋/羽田/有明×2/台北×2） ============
INSERT INTO `live_event` (`title`, `band_id`, `description`, `poster_url`, `venue`, `status`)
SELECT 'Ave Mujica LIVE TOUR 2026「Exitus」', 2,
       '2026年4月17日～8月9日 全国巡演共 8 场：Zepp Fukuoka（4.17）、Zepp Namba（4.26）、Zepp Nagoya（5.1）、Zepp Haneda TOKYO（5.4）、SGC HALL ARIAKE（6.19/6.20）、台北・國立體育大學綜合體育館 林口體育館（8.8/8.9）。各场票价不同，站内统一以 8,800 参考价占位，实际以官方为准。',
       '/img/band-mujica.png', '全国ツアー（8 公演）', 'ENDED'
WHERE NOT EXISTS (SELECT 1 FROM `live_event` WHERE `title` = 'Ave Mujica LIVE TOUR 2026「Exitus」');

INSERT INTO `live_session` (`event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`)
SELECT e.id, x.name, x.st, x.et, '2026-03-01 12:00:00', 'ENDED'
FROM `live_event` e
JOIN (SELECT '福岡（4.17 Zepp Fukuoka）' AS name, '2026-04-17 18:30:00' AS st, '2026-04-17 21:00:00' AS et
      UNION ALL SELECT '大阪（4.26 Zepp Namba）', '2026-04-26 18:30:00', '2026-04-26 21:00:00'
      UNION ALL SELECT '名古屋（5.1 Zepp Nagoya）', '2026-05-01 18:30:00', '2026-05-01 21:00:00'
      UNION ALL SELECT '東京（5.4 Zepp Haneda）', '2026-05-04 18:30:00', '2026-05-04 21:00:00'
      UNION ALL SELECT '有明（6.19 SGC HALL ARIAKE）', '2026-06-19 18:30:00', '2026-06-19 21:00:00'
      UNION ALL SELECT '有明（6.20 SGC HALL ARIAKE）', '2026-06-20 17:30:00', '2026-06-20 20:00:00'
      UNION ALL SELECT '台北（8.8 林口體育館）', '2026-08-08 19:00:00', '2026-08-08 21:30:00'
      UNION ALL SELECT '台北（8.9 林口體育館）', '2026-08-09 17:00:00', '2026-08-09 19:30:00') x
WHERE e.`title` = 'Ave Mujica LIVE TOUR 2026「Exitus」'
  AND NOT EXISTS (SELECT 1 FROM `live_session` s WHERE s.`event_id` = e.id AND s.`name` = x.name);

INSERT INTO `ticket_type` (`session_id`, `name`, `price`, `total_stock`, `sold_count`, `max_per_user`, `status`)
SELECT s.id, '一般指定席（参考价）', 8800.00, 100, 0, 2, 1
FROM `live_session` s
JOIN `live_event` e ON e.id = s.`event_id`
WHERE e.`title` = 'Ave Mujica LIVE TOUR 2026「Exitus」'
  AND s.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ticket_type` tt
                  WHERE tt.`session_id` = s.id AND tt.`name` = '一般指定席（参考价）' AND tt.`is_deleted` = 0);

-- ============ 4. BEAT AX -SUMMER EDITION 2026-（2026-07-12 Kアリーナ横浜，RAISE A SUILEN × Ave Mujica） ============
INSERT INTO `live_event` (`title`, `band_id`, `description`, `poster_url`, `venue`, `status`)
SELECT 'BEAT AX -SUMMER EDITION 2026-', 2,
       '2026年7月12日 Kアリーナ横浜。RAISE A SUILEN × Ave Mujica 拼盘公演（RAISE A SUILEN 未收录于本站乐队库）。票价未取到官方档位，站内以 8,800 参考价占位。',
       '/img/band-mujica.png', 'Kアリーナ横浜', 'ENDED'
WHERE NOT EXISTS (SELECT 1 FROM `live_event` WHERE `title` = 'BEAT AX -SUMMER EDITION 2026-');

INSERT INTO `live_session` (`event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`)
SELECT e.id, '本公演（7.12 横浜）', '2026-07-12 17:00:00', '2026-07-12 20:00:00', '2026-05-15 12:00:00', 'ENDED'
FROM `live_event` e
WHERE e.`title` = 'BEAT AX -SUMMER EDITION 2026-'
  AND NOT EXISTS (SELECT 1 FROM `live_session` s WHERE s.`event_id` = e.id AND s.`name` = '本公演（7.12 横浜）');

INSERT INTO `ticket_type` (`session_id`, `name`, `price`, `total_stock`, `sold_count`, `max_per_user`, `status`)
SELECT s.id, '一般指定席（参考价）', 8800.00, 100, 0, 2, 1
FROM `live_session` s
JOIN `live_event` e ON e.id = s.`event_id`
WHERE e.`title` = 'BEAT AX -SUMMER EDITION 2026-'
  AND s.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `ticket_type` tt
                  WHERE tt.`session_id` = s.id AND tt.`name` = '一般指定席（参考价）' AND tt.`is_deleted` = 0);
