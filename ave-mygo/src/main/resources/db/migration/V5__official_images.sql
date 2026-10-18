-- =====================================================================
-- V5__official_images.sql —— 官方图片落位
-- 素材来源：anime.bang-dream.com 官方公开页面（©BanG Dream! Project）。
-- 本项目非营利粉丝作品，仅作学习演示用途。
-- =====================================================================

-- 乐队主视觉（BandView 头图）
UPDATE band SET `logo_url` = '/img/band-mygo.jpg'   WHERE `slug` = 'mygo';
UPDATE band SET `logo_url` = '/img/band-mujica.png' WHERE `slug` = 'ave-mujica';

-- 成员立绘（立牌卡，id 顺序对应 V4 种子）
UPDATE member SET `avatar_url` = '/img/member-mygo-1.png'   WHERE `id` = 1;   -- 高松燈
UPDATE member SET `avatar_url` = '/img/member-mygo-2.png'   WHERE `id` = 2;   -- 千早愛音
UPDATE member SET `avatar_url` = '/img/member-mygo-3.png'   WHERE `id` = 3;   -- 要樂奈
UPDATE member SET `avatar_url` = '/img/member-mygo-4.png'   WHERE `id` = 4;   -- 長崎そよ
UPDATE member SET `avatar_url` = '/img/member-mygo-5.png'   WHERE `id` = 5;   -- 椎名立希
UPDATE member SET `avatar_url` = '/img/member-mujica-1.png' WHERE `id` = 6;   -- 三角初華
UPDATE member SET `avatar_url` = '/img/member-mujica-2.png' WHERE `id` = 7;   -- 若葉睦
UPDATE member SET `avatar_url` = '/img/member-mujica-3.png' WHERE `id` = 8;   -- 八幡海鈴
UPDATE member SET `avatar_url` = '/img/member-mujica-4.png' WHERE `id` = 9;   -- 祐天寺にゃむ
UPDATE member SET `avatar_url` = '/img/member-mujica-5.png' WHERE `id` = 10;  -- 豊川祥子

-- 演出海报
UPDATE live_event SET `poster_url` = '/img/band-mygo.jpg' WHERE `id` = 1;
