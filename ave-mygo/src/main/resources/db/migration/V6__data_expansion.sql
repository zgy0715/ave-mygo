-- =====================================================================
-- V6__data_expansion.sql —— 曲库/演出数据扩充
-- 曲目基于公开资料整理（与前端考据数据一致）；时长为近似值。
-- 同时激活 setlist_item（演出曲表）与第二场演出。
-- =====================================================================

-- ---------- 1. 修正既有专辑的准确发行信息 ----------
UPDATE album SET `name` = '迷星叫',           `release_date` = '2022-11-09' WHERE `id` = 1;
UPDATE album SET `name` = '音一会',           `release_date` = '2023-01-11' WHERE `id` = 2;
UPDATE album SET `name` = '壱雫空',           `release_date` = '2023-04-12' WHERE `id` = 3;
UPDATE album SET `name` = '黒のバースデイ',   `release_date` = '2023-02-15' WHERE `id` = 4;
UPDATE album SET `name` = 'Ave Mujicaの旗印', `release_date` = '2023-04-05' WHERE `id` = 5;

-- ---------- 2. 新增专辑 ----------
INSERT INTO album (`id`, `band_id`, `name`, `type`, `release_date`) VALUES
(6,  1, '栞',                       'SINGLE', '2023-05-24'),
(7,  1, '閉析',                     'ALBUM',  '2023-08-09'),
(8,  1, '跡空',                     'ALBUM',  '2023-12-13'),
(9,  1, 'TV 动画《MyGO!!!!!》剧中曲', 'OTHER',  '2023-07-27'),
(10, 2, 'TV 动画《Ave Mujica》剧中曲', 'OTHER',  '2025-01-15');

-- ---------- 3. 修正既有歌曲的归属专辑/日期，并补充新曲 ----------
UPDATE song SET `release_date` = '2022-11-09' WHERE `id` = 1;   -- 迷星叫
UPDATE song SET `release_date` = '2023-01-11' WHERE `id` = 2;   -- 音一会
UPDATE song SET `release_date` = '2023-04-12', `album_id` = 3, `track_no` = 2 WHERE `id` = 3;   -- 壱雫空
UPDATE song SET `release_date` = '2023-02-15', `album_id` = 4 WHERE `id` = 7;   -- 黒のバースデイ
UPDATE song SET `release_date` = '2023-04-05', `album_id` = 5, `track_no` = 1 WHERE `id` = 8;   -- Ave Mujica
UPDATE song SET `release_date` = '2023-04-05', `album_id` = 5, `track_no` = 2 WHERE `id` = 9;   -- 素晴らしき世界

INSERT INTO song
(`id`, `title`, `band_id`, `album_id`, `track_no`, `release_date`, `duration`, `tags`, `status`) VALUES
(10, '栞',             1, 6,    1, '2023-05-24', 262, '治愈,催泪',      1),
(11, '碧天伴走',       1, 6,    2, '2023-05-24', 240, '燃',            1),
(12, '忘れてやまない', 1, 7,    1, '2023-08-09', 235, '燃',            1),
(13, '無路矢',         1, 7,    2, '2023-08-09', 251, '暗,燃',         1),
(14, '詩超絆',         1, 8,    1, '2023-12-13', 288, '催泪,名场面',   1),
(15, '春日影',         1, 9,    1, '2023-07-27', 254, 'CRYCHIC,名场面', 1),
(16, '顔',             2, 10,   1, '2025-01-15', 230, '暗,燃',         1);

-- ---------- 4. 第二场演出（Ave Mujica 场） ----------
INSERT INTO live_event (`id`, `title`, `band_id`, `description`, `venue`, `status`) VALUES
(2, 'Ave Mujica 1st LIVE「Masked horizon」', 2,
 '面具下的完美人偶剧团首次专场。幕布升起之前，谁也不知道人偶在想什么。',
 '東京ガーデンシアター', 'PUBLISHED');

INSERT INTO live_session (`id`, `event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`) VALUES
(3, 2, '深夜场', '2026-11-21 19:00:00', '2026-11-21 21:00:00', '2026-10-05 12:00:00', 'ONSALE');

INSERT INTO ticket_type (`id`, `session_id`, `name`, `price`, `total_stock`, `sold_count`, `max_per_user`, `status`) VALUES
(5, 3, 'S席',    880.00, 40,  0, 2, 1),
(6, 3, '普通席', 480.00, 120, 0, 4, 1);

-- ---------- 5. 演出曲表（激活 setlist_item）----------
-- 昼场：MyGO 本篇 + 安可《春日影》（就是那首禁止再奏的曲子）
INSERT INTO setlist_item (`session_id`, `song_id`, `order_no`, `is_encore`) VALUES
(1, 3,  1, 0),
(1, 4,  2, 0),
(1, 12, 3, 0),
(1, 13, 4, 0),
(1, 10, 5, 0),
(1, 11, 6, 0),
(1, 14, 7, 0),
(1, 1,  8, 1),
(1, 15, 9, 1);

-- 夜场：围绕《春日影》之后的他们
INSERT INTO setlist_item (`session_id`, `song_id`, `order_no`, `is_encore`) VALUES
(2, 1,  1, 0),
(2, 2,  2, 0),
(2, 5,  3, 0),
(2, 4,  4, 0),
(2, 14, 5, 0),
(2, 15, 6, 1);
