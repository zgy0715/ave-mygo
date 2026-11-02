-- V48: 专辑归属补全 + 专辑日期修正 + 幻影数据清理（2026-10-22）
-- 依据：Bandori Wiki (bandori.miraheze.org) + bang-dream.com 官方 discography + 百度百科（BRMM 编号交叉核对）
-- 变更：新建 5 张专辑、25 首歌归属、5 张专辑发售日修正、软删幻影歌 48

-- ========== 1. 新建专辑（INSERT IGNORE：允许与已手工应用的环境重放不冲突） ==========
INSERT IGNORE INTO `album` (`id`, `band_id`, `name`, `type`, `release_date`) VALUES
  (16, 1, '致並跡', 'ALBUM', '2026-07-15'),                              -- MyGO!!!!! 3rd Album BRMM-11026~28
  (17, 1, 'バンドリ!カバーコレクション Extra Volume', 'ALBUM', '2024-07-10'), -- 翻唱合集 BRMM-10810/11
  (18, 2, 'ELEMENTS', 'EP', '2024-10-02'),                                -- Ave Mujica 2nd Mini-Album BRMM-10835~37
  (19, 2, 'DIVINE', 'SINGLE', '2025-08-04'),                              -- 数字单曲（游戏 PROGRESS ORDERS OP）
  (20, 2, '碧い瞳の中に', 'SINGLE', '2025-09-04'),                        -- 数字单曲（明日方舟联动曲）
  (21, 2, '‘S/’ The Way / Sophie', 'SINGLE', '2025-12-10');               -- 3rd Single（iTunes JP 官方发售日）

-- ========== 2. 歌曲归属 ==========
-- MyGO!!!!! → 跡暖空(8)（4th/5th Single c/w 收录于 2nd Album）
UPDATE `song` SET `album_id` = 8  WHERE `id` = 19 AND `album_id` IS NULL; -- 処救生
UPDATE `song` SET `album_id` = 8  WHERE `id` = 20 AND `album_id` IS NULL; -- 輪符雨
UPDATE `song` SET `album_id` = 8  WHERE `id` = 21 AND `album_id` IS NULL; -- 砂寸奏／回層浮
UPDATE `song` SET `album_id` = 8  WHERE `id` = 22 AND `album_id` IS NULL; -- 端程山
-- MyGO!!!!! → 致並跡(16)
UPDATE `song` SET `album_id` = 16 WHERE `id` = 27 AND `album_id` IS NULL; -- 聿日箋秋
UPDATE `song` SET `album_id` = 16 WHERE `id` = 28 AND `album_id` IS NULL; -- 残痕字 (TV Size)
UPDATE `song` SET `album_id` = 16 WHERE `id` = 29 AND `album_id` IS NULL; -- 往欄印
UPDATE `song` SET `album_id` = 16 WHERE `id` = 30 AND `album_id` IS NULL; -- エガクミライ
UPDATE `song` SET `album_id` = 16 WHERE `id` = 31 AND `album_id` IS NULL; -- 静降想
UPDATE `song` SET `album_id` = 16 WHERE `id` = 32 AND `album_id` IS NULL; -- 証命讃歌
-- MyGO!!!!! → カバーコレクション Extra Volume(17)
UPDATE `song` SET `album_id` = 17 WHERE `id` = 33 AND `album_id` IS NULL; -- 過去を喰らう (Cover)
UPDATE `song` SET `album_id` = 17 WHERE `id` = 34 AND `album_id` IS NULL; -- だれかの心臓になれたなら (Cover)
UPDATE `song` SET `album_id` = 17 WHERE `id` = 35 AND `album_id` IS NULL; -- ノンブレス·オブリージュ (Cover)
UPDATE `song` SET `album_id` = 17 WHERE `id` = 36 AND `album_id` IS NULL; -- バンドリ!カバーコレクション
-- Ave Mujica → Alea jacta est(13)（官方曲目表第 2~5 轨）
UPDATE `song` SET `album_id` = 13 WHERE `id` = 38 AND `album_id` IS NULL; -- ふたつの月
UPDATE `song` SET `album_id` = 13 WHERE `id` = 39 AND `album_id` IS NULL; -- Choir 'S' Choir
UPDATE `song` SET `album_id` = 13 WHERE `id` = 40 AND `album_id` IS NULL; -- 神さま、バカ
UPDATE `song` SET `album_id` = 13 WHERE `id` = 41 AND `album_id` IS NULL; -- Masquerade Rhapsody Request
-- Ave Mujica → 素晴らしき世界 でも どこにもない場所(14)（1st 单曲双A面，Angles 同单）
UPDATE `song` SET `album_id` = 14 WHERE `id` = 42 AND `album_id` IS NULL; -- Angles
-- Ave Mujica → ELEMENTS(18)
UPDATE `song` SET `album_id` = 18 WHERE `id` = 43 AND `album_id` IS NULL; -- Symbol I
UPDATE `song` SET `album_id` = 18 WHERE `id` = 44 AND `album_id` IS NULL; -- Symbol II Air
UPDATE `song` SET `album_id` = 18 WHERE `id` = 45 AND `album_id` IS NULL; -- Symbol III
UPDATE `song` SET `album_id` = 18 WHERE `id` = 46 AND `album_id` IS NULL; -- Symbol IV Earth
-- Ave Mujica → 数字单曲
UPDATE `song` SET `album_id` = 19 WHERE `id` = 54 AND `album_id` IS NULL; -- DIVINE
UPDATE `song` SET `album_id` = 20 WHERE `id` = 55 AND `album_id` IS NULL; -- in your blue eyes
-- Ave Mujica → 'S/' The Way / Sophie(21)（3rd Single 双 A 面）
UPDATE `song` SET `album_id` = 21 WHERE `id` = 56 AND `album_id` IS NULL; -- S The Way

-- 维持无归属（官方即无专辑）：
-- 37 潜在表明 - From THE FIRST TAKE（TFTV 现场版，非专辑曲目）
-- 62 人間になりたいうたII（GBP 游戏曲）

-- ========== 3. 幻影数据：软删歌 48「ELEMENTS」 ==========
-- 官方 EP《ELEMENTS》仅收 Symbol I:△ / Symbol II:Air / Symbol III:▽ / Symbol IV:Earth / Ether 五曲，
-- 不存在名为 ELEMENTS 的曲目（EP 名 ≠ 歌名），与此前「Ave Mujicaの旗印」幻影专辑同性质。
UPDATE `song` SET `is_deleted` = 1 WHERE `id` = 48 AND `title` = 'ELEMENTS';

-- ========== 4. 专辑发售日修正（官方 BRMM 页面为准） ==========
UPDATE `album` SET `release_date` = '2023-04-12' WHERE `id` = 2  AND `name` = '音一会';      -- 2nd Single，原 2023-01-11 有误
UPDATE `album` SET `release_date` = '2023-08-09' WHERE `id` = 3  AND `name` = '壱雫空';      -- 3rd Single，原 2023-04-12 有误
UPDATE `album` SET `release_date` = '2023-04-11' WHERE `id` = 4  AND `name` = '黒のバースデイ'; -- 1st 数字单曲，原 2023-02-15 有误
UPDATE `album` SET `release_date` = '2023-11-01' WHERE `id` = 7  AND `name` = '迷跡波';      -- 1st Album BRMM-10716，原 2023-08-09 有误
UPDATE `album` SET `release_date` = '2024-12-18' WHERE `id` = 8  AND `name` = '跡暖空';      -- 2nd Album BRMM-10875，原 2023-12-13 有误
