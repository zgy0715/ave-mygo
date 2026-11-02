-- =====================================================================
-- V3__event_seed.sql —— 开发用演出种子数据
-- 一场 MyGO!!!! 纪念 Live：昼夜双场次，S席/普通席票档。
-- 时间设定技巧：开售时间在过去（已开售）、开演在未来（未结束）→ 天然 ONSALE。
-- =====================================================================

INSERT INTO live_event (`id`, `title`, `band_id`, `description`, `venue`, `status`) VALUES
(1, '《迷星叫》发卖纪念 LIVE', 1,
 '为纪念首张单曲《迷星叫》发售而举办的专场演出。舞台上的迷茫与呐喊，都将化作向前一步的力量。',
 'Live House 东京', 'PUBLISHED');

INSERT INTO live_session (`id`, `event_id`, `name`, `start_time`, `end_time`, `sale_start_time`, `status`) VALUES
(1, 1, '昼场', '2026-11-01 14:00:00', '2026-11-01 16:00:00', '2026-10-01 12:00:00', 'ONSALE'),
(2, 1, '夜场', '2026-11-01 18:00:00', '2026-11-01 20:00:00', '2026-10-01 12:00:00', 'ONSALE');

INSERT INTO ticket_type (`id`, `session_id`, `name`, `price`, `total_stock`, `sold_count`, `max_per_user`, `status`) VALUES
(1, 1, 'S席',    680.00, 50,  0, 4, 1),
(2, 1, '普通席', 380.00, 100, 0, 4, 1),
(3, 2, 'S席',    680.00, 50,  0, 4, 1),
(4, 2, '普通席', 380.00, 100, 0, 4, 1);
