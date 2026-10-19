-- =====================================================================
-- V16__album_fix_and_covers.sql —— 专辑正名 + 补齐封面（依据官方核实结论）
-- 事实依据：MyGO!!!!! 1st 专辑=迷跡波、2nd 专辑=跡暖空（用户逐张核对官方封面时确认；
--           此前 DB 中的「閉析/跡空」为早期凭记忆写入的错误名称）。
-- =====================================================================

-- 专辑正名 + 官方封面（图片已存在于 frontend/public/img/covers/）
UPDATE `album` SET `name` = '迷跡波', `cover_url` = '/img/covers/song-17.jpg' WHERE `id` = 7;
UPDATE `album` SET `name` = '跡暖空', `cover_url` = '/img/covers/song-26.jpg' WHERE `id` = 8;

-- 影色舞：单曲专辑补官方封面
UPDATE `album` SET `cover_url` = '/img/covers/song-5.jpg' WHERE `id` = 11;

-- TV 剧中曲合集：无独立专辑图，复用乐队官方 KV
UPDATE `album` SET `cover_url` = '/img/band-mygo.jpg'   WHERE `id` = 9;
UPDATE `album` SET `cover_url` = '/img/band-mujica.png' WHERE `id` = 10;

-- album 5「Ave Mujicaの旗印」：官方图暂缺，保持 NULL 走渐变回退，取得图后另起迁移
