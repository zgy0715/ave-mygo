-- =====================================================================
-- V27__bangumi_insert_songs.sql —— 番剧插入曲官方片段（B站正版番剧 ep 内嵌）
-- 来源：《BanG Dream! Ave Mujica》(颂乐人偶 md23679586) 番剧页"插入曲"专区，
-- 每个条目为官方分集片段，ep_id 经页面核对。播放器格式 player.html?ep_id=
-- =====================================================================

-- 顔（Alter Ego） #13 插曲
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1574279&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 16;
-- 天球(そら)のMúsica  #13 插曲
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1555167&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 53;
-- 聿日箋秋 #13 插曲
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1555168&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 27;
-- Imprisoned XII  #10 插曲（原 YouTube 链接升级为 B 站内嵌）
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1521592&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 51;
-- Crucifix X  #10 插曲
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1498496&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 52;
-- 春日影 #7 插曲（CRYCHIC ver.，替换 YouTube 链接）
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1456216&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 15;
-- KiLLKiSS #1 插曲（替换 YouTube 链接）
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1365111&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 49;
-- Georgette Me, Georgette You ED
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1365126&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 50;
