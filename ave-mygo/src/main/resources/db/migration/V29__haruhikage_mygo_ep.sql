-- =====================================================================
-- V29__haruhikage_mygo_ep.sql —— 《春日影》MV 更正为 MyGO #07 名场面版
-- 说明：春日影有多个官方分集版本，站长指定使用《迷途之子!!!!!》#07 (ep1185679)。
-- =====================================================================

UPDATE song SET `mv_url` = 'https://player.bilibili.com/player.html?ep_id=1185679&autoplay=0&danmaku=0', `mv_platform` = 'BILIBILI' WHERE `id` = 15;
