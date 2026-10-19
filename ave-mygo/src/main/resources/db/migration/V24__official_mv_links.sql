-- =====================================================================
-- V24__official_mv_links.sql —— 官方 MV 外链（第一批，12 首）
-- B站来源：官方频道 MyGO_AveMujica（uid 1459104794）投稿，BV 号经萌百/Listen1
-- 导出清单交叉核对；YouTube 来源：官方频道，前端渲染为外链按钮。
-- 红线：只填官方来源，无官方 MV 的曲保持 NULL。
-- =====================================================================

-- ---- BILIBILI（内嵌播放器）----
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1Ue4y117Xz&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 1;    -- 迷星叫
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1Zs4y1m7HP&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 2;    -- 音一会
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1Lh411c7Ys&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 3;    -- 壱雫空（TV动画OP）
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1xP4y1i7G8&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 4;    -- 潜在表明
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1gP411u7kS&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 5;    -- 影色舞
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1kj411i7ZY&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 14;   -- 詩超絆
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV11m4y1T7mS&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 18;   -- 迷路日々

-- ---- YOUTUBE（官方频道外链按钮）----
UPDATE song SET `mv_url` = 'https://youtu.be/ZsvJUh03MwI', `mv_platform` = 'YOUTUBE' WHERE `id` = 15;   -- 春日影（MyGO!!!!! ver.）
UPDATE song SET `mv_url` = 'https://youtu.be/wuUZjdiUCj0', `mv_platform` = 'YOUTUBE' WHERE `id` = 6;    -- 栞
UPDATE song SET `mv_url` = 'https://youtu.be/yo0gaLx7XdQ', `mv_platform` = 'YOUTUBE' WHERE `id` = 13;   -- 無路矢
UPDATE song SET `mv_url` = 'https://youtu.be/RexyoaXaQ1o', `mv_platform` = 'YOUTUBE' WHERE `id` = 49;   -- KiLLKiSS
UPDATE song SET `mv_url` = 'https://youtu.be/0YNMV7xljD4', `mv_platform` = 'YOUTUBE' WHERE `id` = 51;   -- Imprisoned XII
