-- =====================================================================
-- V28__mygo_insert_songs.sql —— MyGO 篇插入曲 ep 内嵌 + 补录《焚音打》
-- 来源：《迷途之子!!!!!》(md?) 番剧 API 插入曲专区 ep 列表，逐条核对：
--   ep1185676 OP(壱雫空) / ep1185683 ED(栞) / ep1185677 #03(春日影)
--   ep1185678 #07(碧天伴走) / ep1185679 #07(春日影 MyGO ver.)
--   ep1185680 #10(詩超絆) / ep1185681 #12(迷星叫) / ep1185682 #12(迷路日々)
-- =====================================================================

-- 栞：ED 版（替换 YouTube 外链为 B 站内嵌）
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1185683&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 6;
-- 碧天伴走：#07 插曲（此前无 MV）
UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1185678&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 11;

-- 补录《焚音打》——3rd Single「壱雫空」C/W 曲（萌百 3rd 单曲 tracklist 核实）
INSERT INTO song (`id`, `title`, `band_id`, `album_id`, `track_no`, `release_date`, `mv_url`, `mv_platform`, `tags`, `status`) VALUES
(60, '焚音打', 1, 3, 3, '2023-04-12',
 'https://player.bilibili.com/player.html?ep_id=1603811&autoplay=0&danmaku=0', 'BILIBILI', '暗,动画插曲', 1)
ON DUPLICATE KEY UPDATE `mv_url` = VALUES(`mv_url`);
