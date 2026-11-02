-- =====================================================================
-- V31__sumimi_mv.sql —— sumimi《Here, the world!》MV 外链（站长指定 B 站视频）
-- BV181421k7F6 经 B 站 view 接口核实：标题含 Here, the world!-sumimi，时长 4:23。
-- 说明：粉丝投稿音乐分享，非官方 MV——官方无独立 MV，按站长规则采用 B 站版本。
-- =====================================================================

UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?bvid=BV181421k7F6&autoplay=0', `mv_platform` = 'BILIBILI' WHERE `id` = 58;
