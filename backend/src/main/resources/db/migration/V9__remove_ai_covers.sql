-- =====================================================================
-- V9__remove_ai_covers.sql —— 撤销 AI 生成封面（用户决定不使用 AI 素材）
-- cover_url 清空后前端自动回退程序化渐变封面；
-- 后续替换官方图时：图片放入 frontend/public/img/covers/ 后重跑类似 UPDATE 即可。
-- =====================================================================

UPDATE album SET `cover_url` = NULL;
UPDATE song SET `cover_url` = NULL;
