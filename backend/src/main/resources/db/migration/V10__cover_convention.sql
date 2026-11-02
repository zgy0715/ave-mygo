-- =====================================================================
-- V10__cover_convention.sql —— 封面约定路径
-- 前端按 /img/covers/cover-{专辑id}.png 自动探测：文件存在即显示，缺失回退渐变。
-- 上图流程：官方专辑图另存为 cover-{id}.png 放入 frontend/public/img/covers/ 即可，无需改库。
-- TV 剧中曲两张专辑直接指向已有的官方 KV。
-- =====================================================================

UPDATE album SET `cover_url` = '/img/covers/cover-1.png'  WHERE `id` = 1;    -- 迷星叫
UPDATE album SET `cover_url` = '/img/covers/cover-2.png'  WHERE `id` = 2;    -- 音一会
UPDATE album SET `cover_url` = '/img/covers/cover-3.png'  WHERE `id` = 3;    -- 壱雫空
UPDATE album SET `cover_url` = '/img/covers/cover-4.png'  WHERE `id` = 4;    -- 黒のバースデイ
UPDATE album SET `cover_url` = '/img/covers/cover-5.png'  WHERE `id` = 5;    -- Ave Mujicaの旗印
UPDATE album SET `cover_url` = '/img/covers/cover-6.png'  WHERE `id` = 6;    -- 栞
UPDATE album SET `cover_url` = '/img/covers/cover-7.png'  WHERE `id` = 7;    -- 閉析
UPDATE album SET `cover_url` = '/img/covers/cover-8.png'  WHERE `id` = 8;    -- 跡空
UPDATE album SET `cover_url` = '/img/covers/cover-9.png'  WHERE `id` = 9;    -- TV 剧中曲(MyGO)
UPDATE album SET `cover_url` = '/img/covers/cover-10.png' WHERE `id` = 10;   -- TV 剧中曲(Mujica)
UPDATE album SET `cover_url` = '/img/covers/cover-11.png' WHERE `id` = 11;   -- 影色舞

-- 歌曲封面继续继承所属专辑
UPDATE song s
JOIN album a ON a.`id` = s.`album_id`
SET s.`cover_url` = a.`cover_url`
WHERE s.`album_id` IS NOT NULL;

-- 演出海报沿用官方 KV（已在 V5/V6 设置，此处确保 Mujica 场也有）
UPDATE live_event SET `poster_url` = '/img/band-mujica.png' WHERE `id` = 2 AND `poster_url` IS NULL;
