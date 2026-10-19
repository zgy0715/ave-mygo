-- =====================================================================
-- V26__stream_links.sql —— 官方平台全曲收听外链（网易云音乐官方页面）
-- 只做外链跳转，不抓取/不转存音频；站内播放仍用 iTunes 30s 官方试听。
-- 未命中者（未上架/命名不含关键词）保持 NULL：前端显示"暂无全曲链接"。
-- =====================================================================

ALTER TABLE `song` ADD COLUMN `stream_url` VARCHAR(255) NULL COMMENT '官方平台全曲收听页' AFTER `audio_url`;

UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2097485069' WHERE `id` = 1;   -- 迷星叫 -> 迷星叫
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2097485075' WHERE `id` = 2;   -- 音一会 -> 音一会
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2060086839' WHERE `id` = 3;   -- 壱雫空 -> 壱雫空
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2097485074' WHERE `id` = 4;   -- 潜在表明 -> 潜在表明
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2097485072' WHERE `id` = 5;   -- 影色舞 -> 影色舞
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2060083093' WHERE `id` = 6;   -- 栞 -> 栞
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2118740086' WHERE `id` = 7;   -- 黒のバースデイ -> 黒のバースデイ
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2085117641' WHERE `id` = 8;   -- Ave Mujica -> Ave Mujica
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2114641236' WHERE `id` = 9;   -- 素晴らしき世界 -> 素晴らしき世界 でも どこにもない場所
UPDATE song SET `stream_url` = 'https://music.163.com/#/song?id=2074119582' WHERE `id` = 11;   -- 碧天伴走 -> 碧天伴走

-- 补：MV（官方无独立MV，采用B站4K中字搬运版，用户规则允许）
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1EjqzBvEms&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 16;   -- 顔
