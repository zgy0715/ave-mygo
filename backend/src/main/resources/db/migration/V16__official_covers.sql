-- =====================================================================
-- V15__official_covers.sql —— 官方封面全量落位（56 张核对成果，55 个 song id）
-- 素材：官方 1400×1400 数字封面 ×53 + 官方游戏曲绘 400×400 ×2（春日影/Ave Mujica）。
-- 来源与核对记录：用户逐张视觉核对（详见 Desktop\图片\封面清单.txt）。
-- ©BanG Dream! Project。本项目非营利粉丝作品，仅作学习演示用途。
-- =====================================================================

-- ---- 数据修正：查无此曲（整个 BanG Dream! 均无《忘れてやまない》）----
DELETE FROM `song` WHERE `id` = 12;

-- ---- MyGO!!!!! 单曲/专辑/版本项 ----
UPDATE `song` SET `cover_url` = '/img/covers/song-1.jpg'  WHERE `id` = 1;    -- 迷星叫
UPDATE `song` SET `cover_url` = '/img/covers/song-2.jpg'  WHERE `id` = 2;    -- 音一会
UPDATE `song` SET `cover_url` = '/img/covers/song-3.jpg'  WHERE `id` = 3;    -- 壱雫空
UPDATE `song` SET `cover_url` = '/img/covers/song-4.jpg'  WHERE `id` = 4;    -- 潜在表明
UPDATE `song` SET `cover_url` = '/img/covers/song-5.jpg'  WHERE `id` = 5;    -- 影色舞
UPDATE `song` SET `cover_url` = '/img/covers/song-6.jpg'  WHERE `id` = 6;    -- 栞
UPDATE `song` SET `cover_url` = '/img/covers/song-11.jpg' WHERE `id` = 11;   -- 碧天伴走
UPDATE `song` SET `cover_url` = '/img/covers/song-13.jpg' WHERE `id` = 13;   -- 無路矢
UPDATE `song` SET `cover_url` = '/img/covers/song-14.png' WHERE `id` = 14;   -- 詩超絆
UPDATE `song` SET `cover_url` = '/img/covers/song-15.png' WHERE `id` = 15;   -- 春日影（CRYCHIC 原曲，400px 游戏曲绘）
UPDATE `song` SET `cover_url` = '/img/covers/song-17.jpg' WHERE `id` = 17;   -- 迷跡波
UPDATE `song` SET `cover_url` = '/img/covers/song-18.jpg' WHERE `id` = 18;   -- 迷路日々
UPDATE `song` SET `cover_url` = '/img/covers/song-19.jpg' WHERE `id` = 19;   -- 処救生
UPDATE `song` SET `cover_url` = '/img/covers/song-20.jpg' WHERE `id` = 20;   -- 輪符雨
UPDATE `song` SET `cover_url` = '/img/covers/song-21.jpg' WHERE `id` = 21;   -- 砂寸奏／回層浮（双 A 面共用）
UPDATE `song` SET `cover_url` = '/img/covers/song-22.jpg' WHERE `id` = 22;   -- 端程山
UPDATE `song` SET `cover_url` = '/img/covers/song-23.jpg' WHERE `id` = 23;   -- 過惰幻
UPDATE `song` SET `cover_url` = '/img/covers/song-24.jpg' WHERE `id` = 24;   -- 霧周途
UPDATE `song` SET `cover_url` = '/img/covers/song-25.jpg' WHERE `id` = 25;   -- 歩拾道
UPDATE `song` SET `cover_url` = '/img/covers/song-26.jpg' WHERE `id` = 26;   -- 跡暖空
UPDATE `song` SET `cover_url` = '/img/covers/song-27.jpg' WHERE `id` = 27;   -- 聿日箋秋
UPDATE `song` SET `cover_url` = '/img/covers/song-28.jpg' WHERE `id` = 28;   -- 残痕字 (TV Size)
UPDATE `song` SET `cover_url` = '/img/covers/song-29.jpg' WHERE `id` = 29;   -- 往欄印
UPDATE `song` SET `cover_url` = '/img/covers/song-30.jpg' WHERE `id` = 30;   -- エガクミライ
UPDATE `song` SET `cover_url` = '/img/covers/song-31.jpg' WHERE `id` = 31;   -- 静降想
UPDATE `song` SET `cover_url` = '/img/covers/song-32.jpg' WHERE `id` = 32;   -- 証命讃歌
UPDATE `song` SET `cover_url` = '/img/covers/song-33.jpg' WHERE `id` = 33;   -- 過去を喰らう (Cover)
UPDATE `song` SET `cover_url` = '/img/covers/song-34.jpg' WHERE `id` = 34;   -- だれかの心臓になれたなら (Cover)
UPDATE `song` SET `cover_url` = '/img/covers/song-35.jpg' WHERE `id` = 35;   -- ノンブレス・オブリージュ (Cover)
UPDATE `song` SET `cover_url` = '/img/covers/song-36.jpg' WHERE `id` = 36;   -- カバーコレクション Extra Volume
UPDATE `song` SET `cover_url` = '/img/covers/song-37.jpg' WHERE `id` = 37;   -- 潜在表明 - From THE FIRST TAKE

-- ---- Ave Mujica ----
UPDATE `song` SET `cover_url` = '/img/covers/song-7.jpg'  WHERE `id` = 7;    -- 黒のバースデイ
UPDATE `song` SET `cover_url` = '/img/covers/song-8.png'  WHERE `id` = 8;    -- Ave Mujica（400px 游戏曲绘）
UPDATE `song` SET `cover_url` = '/img/covers/song-9.jpg'  WHERE `id` = 9;    -- 素晴らしき世界
UPDATE `song` SET `cover_url` = '/img/covers/song-16.jpg' WHERE `id` = 16;   -- 顔
UPDATE `song` SET `cover_url` = '/img/covers/song-38.jpg' WHERE `id` = 38;   -- ふたつの月 ~Deep Into The Forest~
UPDATE `song` SET `cover_url` = '/img/covers/song-39.jpg' WHERE `id` = 39;   -- Choir 'S' Choir
UPDATE `song` SET `cover_url` = '/img/covers/song-40.jpg' WHERE `id` = 40;   -- 神さま、バカ
UPDATE `song` SET `cover_url` = '/img/covers/song-41.jpg' WHERE `id` = 41;   -- Masquerade Rhapsody Request
UPDATE `song` SET `cover_url` = '/img/covers/song-42.jpg' WHERE `id` = 42;   -- Angles
UPDATE `song` SET `cover_url` = '/img/covers/song-43.jpg' WHERE `id` = 43;   -- Symbol I
UPDATE `song` SET `cover_url` = '/img/covers/song-44.jpg' WHERE `id` = 44;   -- Symbol II Air
UPDATE `song` SET `cover_url` = '/img/covers/song-45.jpg' WHERE `id` = 45;   -- Symbol III
UPDATE `song` SET `cover_url` = '/img/covers/song-46.jpg' WHERE `id` = 46;   -- Symbol IV Earth
UPDATE `song` SET `cover_url` = '/img/covers/song-47.jpg' WHERE `id` = 47;   -- 素晴らしき世界 でも どこにもない場所
UPDATE `song` SET `cover_url` = '/img/covers/song-48.jpg' WHERE `id` = 48;   -- ELEMENTS
UPDATE `song` SET `cover_url` = '/img/covers/song-49.jpg' WHERE `id` = 49;   -- KiLLKiSS
UPDATE `song` SET `cover_url` = '/img/covers/song-50.jpg' WHERE `id` = 50;   -- Georgette Me, Georgette You
UPDATE `song` SET `cover_url` = '/img/covers/song-51.jpg' WHERE `id` = 51;   -- Imprisoned XII
UPDATE `song` SET `cover_url` = '/img/covers/song-52.jpg' WHERE `id` = 52;   -- Crucifix X
UPDATE `song` SET `cover_url` = '/img/covers/song-53.jpg' WHERE `id` = 53;   -- 天球(そら)のMúsica
UPDATE `song` SET `cover_url` = '/img/covers/song-54.jpg' WHERE `id` = 54;   -- DIVINE
UPDATE `song` SET `cover_url` = '/img/covers/song-55.jpg' WHERE `id` = 55;   -- in your blue eyes
UPDATE `song` SET `cover_url` = '/img/covers/song-56.jpg' WHERE `id` = 56;   -- 'S/' The Way
UPDATE `song` SET `cover_url` = '/img/covers/song-57.jpg' WHERE `id` = 57;   -- Completeness

-- ---- 专辑封面：与官方单曲图一一对应的直接落位 ----
UPDATE `album` SET `cover_url` = '/img/covers/song-1.jpg' WHERE `id` = 1;    -- 1st Single 迷星叫
UPDATE `album` SET `cover_url` = '/img/covers/song-2.jpg' WHERE `id` = 2;    -- 2nd Single 音一会
UPDATE `album` SET `cover_url` = '/img/covers/song-3.jpg' WHERE `id` = 3;    -- 3rd Single 壱雫空
UPDATE `album` SET `cover_url` = '/img/covers/song-7.jpg' WHERE `id` = 4;    -- 1st Single 黒のバースデイ
UPDATE `album` SET `cover_url` = '/img/covers/song-6.jpg' WHERE `id` = 6;    -- 4th Single 栞

-- 待决事项（不在本迁移处理）：
-- 1. album 7「閉析」/8「跡空」与官方专辑名（迷跡波=1st / 跡暖空=2nd）存在出入，需确认后另起迁移
-- 2. album 5「Ave Mujicaの旗印」官方封面不在本次 56 张内
-- 3. album 9/10（TV 剧中曲）复用歌曲封面即可，不单独设图
