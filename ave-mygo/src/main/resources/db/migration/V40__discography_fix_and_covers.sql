-- =====================================================================
-- V40__discography_fix_and_covers.sql —— 专辑归属纠正 + 补录曲封面
-- 依据官方 tracklist（萌百/Bandori Fandom 已核对）：
--   2nd Single 音一会(2023-04-12)：音一会 / 潜在表明 / 影色舞
--   3rd Single 壱雫空(2023-08-09)：壱雫空 / 栞 / 焚音打
--   1st Album 迷跡波(2023-11-01)：碧天伴走 / 歌いましょう鳴らしましょう / 春日影(MyGO ver) /
--                                 詩超絆 / 迷路日々 / 無路矢 …
--   2nd Album 跡暖空：歩拾道 / 明弦音 / 霧周途 / 夜隠染 / 過惰幻
-- 原则：只做有官方曲序依据的挂靠；无依据的一律保持 NULL，不新建"同名单曲专辑"凑数。
-- 幂等：纯 UPDATE/DELETE/INSERT，可重复执行。
-- =====================================================================

-- ① 补录曲封面（此前 NULL，前台回退渐变）
UPDATE song SET cover_url='/img/covers/song-3.png'  WHERE id=60 AND cover_url IS NULL;  -- 焚音打
UPDATE song SET cover_url='/img/covers/song-57.jpg' WHERE id=61 AND cover_url IS NULL;  -- 八芒星ダンス
UPDATE song SET cover_url='/img/covers/song-15.png' WHERE id=62 AND cover_url IS NULL;  -- 人間になりたいうたII

-- ② 2nd Single 音一会：潜在表明(T2) / 影色舞(T3)；原"影色舞"幽灵单曲专辑清空后删除
UPDATE song SET album_id=2, track_no=2 WHERE id=4;    -- 潜在表明
UPDATE song SET album_id=2, track_no=3 WHERE id=5;    -- 影色舞
DELETE FROM album WHERE id=11 AND NOT EXISTS (SELECT 1 FROM song WHERE album_id=11);

-- ③ 3rd Single 壱雫空：壱雫空(T1) / 栞(T2) / 焚音打(T3)；栞原挂在错误的独立单曲上
UPDATE song SET album_id=3, track_no=1 WHERE id=3;    -- 壱雫空
UPDATE song SET album_id=3, track_no=2 WHERE id=6;    -- 栞
UPDATE song SET album_id=3, track_no=3 WHERE id=60;   -- 焚音打
DELETE FROM album WHERE id=6 AND NOT EXISTS (SELECT 1 FROM song WHERE album_id=6);

-- ④ 1st Album 迷跡波：碧天伴走 / 春日影(MyGO ver) / 詩超絆 / 迷路日々 / 無路矢
UPDATE song SET album_id=7, track_no=1 WHERE id=11;   -- 碧天伴走
UPDATE song SET album_id=7, track_no=3 WHERE id=15;   -- 春日影(MyGO ver.)
UPDATE song SET album_id=7, track_no=4 WHERE id=14;   -- 詩超絆
UPDATE song SET album_id=7, track_no=5 WHERE id=18;   -- 迷路日々
UPDATE song SET album_id=7, track_no=6 WHERE id=13;   -- 無路矢
DELETE FROM album WHERE id=9 AND NOT EXISTS (SELECT 1 FROM song WHERE album_id=9);

-- ⑤ 2nd Album 跡暖空：歩拾道 / 霧周途 / 過惰幻（明弦音、夜隠染 尚未入库）
UPDATE song SET album_id=8, track_no=1 WHERE id=25;   -- 歩拾道
UPDATE song SET album_id=8, track_no=3 WHERE id=24;   -- 霧周途
UPDATE song SET album_id=8, track_no=5 WHERE id=23;   -- 過惰幻

-- ⑥ 空壳合集专辑（曲目已归入 Completeness / 各单曲）
DELETE FROM album WHERE id=10 AND NOT EXISTS (SELECT 1 FROM song WHERE album_id=10);

-- ⑦ 收尾：残留空专辑（栞 已并入 3rd Single）与 sumimi 单曲挂靠
DELETE FROM album WHERE id=6 AND NOT EXISTS (SELECT 1 FROM song WHERE album_id=6);
UPDATE song SET album_id=12, track_no=1 WHERE id=58;   -- Here, the world!
