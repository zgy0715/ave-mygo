-- =====================================================================
-- V8__fix_duplicates.sql —— 修复曲目重复与缺专辑
-- 问题1：《栞》在 V2(id=6, 无专辑) 与 V6(id=10, 有专辑) 重复 → 合并保留 id=6
-- 问题2：《影色舞》(id=5) 从未挂专辑 → 补建 6th Single 专辑并落封面
-- =====================================================================

-- 1. V2 版《栞》归入 4th Single 专辑（V6 版 id=10 的数据与此重复）
UPDATE song SET `album_id` = 6, `track_no` = 1, `release_date` = '2023-05-24' WHERE `id` = 6;

-- 演出曲表里引用 id=10 的条目改指向 id=6，再删除重复行
UPDATE setlist_item SET `song_id` = 6 WHERE `song_id` = 10;
DELETE FROM song WHERE `id` = 10;

-- 2.《影色舞》补专辑（6th Single）与封面
INSERT INTO album (`id`, `band_id`, `name`, `type`, `release_date`) VALUES
(11, 1, '影色舞', 'SINGLE', '2024-01-11');

UPDATE song SET `album_id` = 11, `track_no` = 1, `release_date` = '2024-01-11' WHERE `id` = 5;
UPDATE album SET `cover_url` = '/img/covers/cover-9.png' WHERE `id` = 11;
UPDATE song SET `cover_url` = '/img/covers/cover-9.png' WHERE `id` = 5;
