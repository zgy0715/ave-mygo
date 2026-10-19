-- =====================================================================
-- V33__bili_full_songs_batch2.sql —— B站完整音频源第二批
-- =====================================================================

UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1W9Nzz7EEt&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=46;   -- Symbol IV Earth（音源单 无损）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1EF4m1j7QF&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=20;   -- 輪符雨（Full Ver. 官方音源）
