-- =====================================================================
-- V20__remove_phantom_album.sql —— 清除幻影专辑「Ave Mujicaの旗印」
-- 事实依据（前端会话核对官方 discography 全目录）：官方无此专辑，
-- album id=5 为 DB 早期凭记忆写入的错误条目。
-- 附带：song 47 与 song 9 为同一首歌的重复条目，合并保留 9。
-- =====================================================================

-- 1. 真 EP：Alea jacta est（2023-09-13）——song 8「Ave Mujica」实收录于此
INSERT INTO album (`id`, `band_id`, `name`, `type`, `release_date`, `cover_url`) VALUES
(13, 2, 'Alea jacta est', 'EP', '2023-09-13', NULL);

UPDATE song SET `album_id` = 13, `release_date` = '2023-09-13' WHERE `id` = 8;

-- 2. 数字单曲：素晴らしき世界 でも どこにもない場所（2023-12-30 配信）
INSERT INTO album (`id`, `band_id`, `name`, `type`, `release_date`, `cover_url`) VALUES
(14, 2, '素晴らしき世界 でも どこにもない場所', 'SINGLE', '2023-12-30', '/img/covers/song-9.jpg');

UPDATE song SET `album_id` = 14, `release_date` = '2023-12-30' WHERE `id` = 9;

-- 3. 合并重复：song 47 与 song 9 同曲，保留 9（无 setlist 引用，删除安全）
DELETE FROM `song` WHERE `id` = 47;

-- 4. 删除幻影专辑
DELETE FROM `album` WHERE `id` = 5;
