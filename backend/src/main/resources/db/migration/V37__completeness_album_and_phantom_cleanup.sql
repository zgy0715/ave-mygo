-- =====================================================================
-- V37__completeness_album_and_phantom_cleanup.sql
-- ① 建 Ave Mujica 1st Album「Completeness」(2025-04-23) —— 此前被误录为"歌曲 id 57"
--    曲目依据 Bandori Fandom 官方专辑页：KiLLKiSS / Georgette Me, Georgette You /
--    Imprisoned XII / Crucifix X / 八芒星ダンス / 顔 / 天球(そら)のMúsica
-- ② 删除三个"专辑名误录为歌曲"的幽灵条目（迷跡波17 / 跡暖空26 / Completeness57），
--    三个名字对应的 album 记录（7/8/15）已存在或本次建立。
-- =====================================================================

INSERT INTO album (`id`, `band_id`, `name`, `type`, `release_date`, `cover_url`) VALUES
(15, 2, 'Completeness', 'ALBUM', '2025-04-23', '/img/covers/song-57.jpg')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `cover_url` = VALUES(`cover_url`);

-- 七首曲目挂入专辑（track_no 按官方曲序）
UPDATE song SET `album_id` = 15, `track_no` = 1 WHERE `id` = 49;   -- KiLLKiSS
UPDATE song SET `album_id` = 15, `track_no` = 2 WHERE `id` = 50;   -- Georgette Me, Georgette You
UPDATE song SET `album_id` = 15, `track_no` = 3 WHERE `id` = 51;   -- Imprisoned XII
UPDATE song SET `album_id` = 15, `track_no` = 4 WHERE `id` = 52;   -- Crucifix X
UPDATE song SET `album_id` = 15, `track_no` = 5 WHERE `id` = 61;   -- 八芒星ダンス
UPDATE song SET `album_id` = 15, `track_no` = 6 WHERE `id` = 16;   -- 顔
UPDATE song SET `album_id` = 15, `track_no` = 7 WHERE `id` = 53;   -- 天球(そら)のMúsica

-- 删除幽灵条目（setlist 零引用，已核对）
DELETE FROM `song` WHERE `id` IN (17, 26, 57);
