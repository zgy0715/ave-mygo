-- =====================================================================
-- V30__missing_insert_songs.sql —— 补录两首动画插入曲（此前库中缺失）
-- 事实来源：萌娘百科《BanG Dream! Ave Mujica》插入曲表 + Bandori Fandom：
--   八芒星ダンス(第13话) 歌:Ave Mujica（收录 1st Album「Completeness」2025-04-23）
--   人間になりたいうたII(第7话) 歌:CRYCHIC
-- ep 来源：番剧 API 插入曲专区（ep1664378 / ep1456209）
-- =====================================================================

INSERT INTO song (`id`, `title`, `band_id`, `mv_url`, `mv_platform`, `tags`, `status`) VALUES
(61, '八芒星ダンス', 2,
 'https://player.bilibili.com/player.html?ep_id=1664378&autoplay=0&danmaku=0', 'BILIBILI', '暗,动画插曲', 1),
(62, '人間になりたいうたII', 1,
 'https://player.bilibili.com/player.html?ep_id=1456209&autoplay=0&danmaku=0', 'BILIBILI', 'CRYCHIC,动画插曲', 1);
