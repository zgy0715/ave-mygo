-- =====================================================================
-- V32__bili_full_songs_batch1.sql —— B站完整音频源第一批（13首）
-- 标准：完整播放整曲（官方音源/live/无损/卡拉OK都算），拒绝翻唱/鼓谱/反应。
-- 每一条均经检索结果标题核对曲名，BV 号可播。
-- =====================================================================

UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1ChKHzAEQF&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=7;    -- 黒のバースデイ（Free Live 完整）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1kUPXeREJ9&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=39;   -- Choir 'S' Choir（0th LIVE 4K）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1KxPdeME2G&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=48;   -- ELEMENTS（CD全轨）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1VPBFYREU2&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=40;   -- 神さま、バカ（4th Live 完整）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1C9Nzz7Ein&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=43;   -- Symbol I（音源单 无损）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1W9Nzz7EJZ&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=45;   -- Symbol III（音源单 无损）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV19D421M73g&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=44;   -- Symbol II Air（2nd Live 完整）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1RodKYWEgr&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=54;   -- DIVINE（完整版）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1EoE9zUEbN&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=42;   -- Angles（4K HiRes 中日双语）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1Q7jdzKEKb&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=59;   -- Sweet Escape（CD无损）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1Qz4y1F7B4&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=19;   -- 処救生（全曲 中日歌词）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1DJkuBUESw&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=56;   -- S The Way（ニコカラ 完整音源）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV13Ma2zjEMA&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=55;   -- in your blue eyes（明日方舟官方EP）
