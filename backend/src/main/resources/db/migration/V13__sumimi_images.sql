-- =====================================================================
-- V13__sumimi_images.sql —— sumimi 官方立绘落位
-- 素材来源：アニメ「BanG Dream! It's MyGO!!!!!」公式サイト角色页
-- （anime.bang-dream.com/mygo/character/uika/ /mana/，sumimi 服装立绘）
-- ©BanG Dream! Project。本项目非营利粉丝作品，仅作学习演示用途。
-- 注：sumimi 官方 logo 暂未获得可下载源，band.logo_url 保持空，
--     BandView 头图自动走粉金渐变回退分支。
-- =====================================================================

-- 成员立绘（V11/V12 修正后的 sumimi 成员 id）
UPDATE member SET `avatar_url` = '/img/member-sumimi-1.png' WHERE `id` = 11;  -- 三角 初華
UPDATE member SET `avatar_url` = '/img/member-sumimi-2.png' WHERE `id` = 12;  -- 純田 まな
