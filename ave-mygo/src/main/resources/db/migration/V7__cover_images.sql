-- =====================================================================
-- V7__cover_images.sql —— 专辑/歌曲封面落位
-- 封面为 AI 生成风格化插画（版权干净，非官方素材）；TV 剧中曲复用官方 KV。
-- =====================================================================

UPDATE album SET `cover_url` = '/img/covers/cover-1.png' WHERE `id` = 1;   -- 迷星叫
UPDATE album SET `cover_url` = '/img/covers/cover-2.png' WHERE `id` = 2;   -- 音一会
UPDATE album SET `cover_url` = '/img/covers/cover-3.png' WHERE `id` = 3;   -- 壱雫空
UPDATE album SET `cover_url` = '/img/covers/cover-7.png' WHERE `id` = 4;   -- 黒のバースデイ
UPDATE album SET `cover_url` = '/img/covers/cover-8.png' WHERE `id` = 5;   -- Ave Mujicaの旗印
UPDATE album SET `cover_url` = '/img/covers/cover-4.png' WHERE `id` = 6;   -- 栞
UPDATE album SET `cover_url` = '/img/covers/cover-5.png' WHERE `id` = 7;   -- 閉析
UPDATE album SET `cover_url` = '/img/covers/cover-6.png' WHERE `id` = 8;   -- 跡空
UPDATE album SET `cover_url` = '/img/band-mygo.jpg'      WHERE `id` = 9;   -- MyGO 剧中曲
UPDATE album SET `cover_url` = '/img/band-mujica.png'    WHERE `id` = 10;  -- Mujica 剧中曲

-- 歌曲封面继承所属专辑
UPDATE song s
JOIN album a ON a.`id` = s.`album_id`
SET s.`cover_url` = a.`cover_url`
WHERE s.`album_id` IS NOT NULL;

-- 演出海报：官方 KV
UPDATE live_event SET `poster_url` = '/img/band-mujica.png' WHERE `id` = 2;
