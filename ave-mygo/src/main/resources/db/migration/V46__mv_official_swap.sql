-- V46: MV 官方源替换（2026-10-22）
-- 依据：用户人工核查 + B站 view API 逐条验证（UP主 mid=1459104794 MyGO_AveMujica / 1299267158 THE FIRST TAKE）
-- 砂寸奏／回層浮为双A面，官方各发一MV；本表一行一mv_url，取「砂寸奏」，回層浮备用 BV1Pp421U7Ln

UPDATE `song` SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1na4y1c7bR&autoplay=0'  WHERE `id` = 6  AND `title` = '栞';                                   -- 栞
UPDATE `song` SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1Xg4y1k7GH&autoplay=0'  WHERE `id` = 9  AND `title` = '素晴らしき世界';                        -- 素晴らしき世界
UPDATE `song` SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1VH4y1E7XE&autoplay=0'  WHERE `id` = 21 AND `title` = '砂寸奏／回層浮';                       -- 砂寸奏
UPDATE `song` SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1hFQWYQE15&autoplay=0'  WHERE `id` = 37 AND `title` = '潜在表明 - From THE FIRST TAKE';       -- THE FIRST TAKE 官方
UPDATE `song` SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1LmmLBpEAs&autoplay=0'  WHERE `id` = 56 AND `title` = 'S The Way';                            -- 'S/' The Way
UPDATE `song` SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV18e411Y7Vz&autoplay=0'  WHERE `id` = 38 AND `title` = 'ふたつの月 ~Deep Into The Forest~';    -- ふたつの月
