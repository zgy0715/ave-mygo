-- =====================================================================
-- V38__official_bili_mv_fix.sql —— 官方 B 站 MV 替换（站内统一 BILIBILI 内嵌）
-- ① 春日影：改为官方 MV（B站 API 核实：BV1JHLUz4EUy，UP=MyGO_AveMujica，4:20）
--    原为番剧 #07 插曲片段（V29），官方完整 MV 体验更好
-- ② 無路矢：为 MyGO!!!!! 曲目（非 Ave Mujica），V24 误挂 YouTube 源，
--    改为 B 站 Divide/Unite 合同 Live 完整版（4K/HiRes 中字）
-- 两条均为纯 UPDATE，可重复执行（幂等）
-- =====================================================================

UPDATE song SET
    `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1JHLUz4EUy&autoplay=0',
    `mv_platform` = 'BILIBILI'
WHERE `id` = 15;

UPDATE song SET
    `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV1ns4GebEfx&autoplay=0',
    `mv_platform` = 'BILIBILI'
WHERE `id` = 13;
