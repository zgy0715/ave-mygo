-- =====================================================================
-- V41__fill_duration_from_bili.sql —— 缺失时长补齐
-- 来源：B 站 view 接口 duration（与站内 MV 同一视频）。
-- 过滤：仅接受 150–360 秒（排除「CD全轨」「合集」等长视频，避免把整张专辑当单曲时长）。
-- 无可信来源者保持 NULL，不做估算。
-- =====================================================================

UPDATE song SET duration=234 WHERE id=21 AND duration IS NULL;   -- 砂寸奏／回層浮（【中字4K·Live】「砂寸奏」- My）
UPDATE song SET duration=280 WHERE id=39 AND duration IS NULL;   -- Choir 'S' Choir（【4K/60FPS/Hi-Res】「Ch）
UPDATE song SET duration=340 WHERE id=55 AND duration IS NULL;   -- in your blue eyes（《明日方舟》EP - in your b）
UPDATE song SET duration=224 WHERE id=59 AND duration IS NULL;   -- Sweet Escape（[CD无损] Sumimi 新曲「Swe）
UPDATE song SET duration=332 WHERE id=60 AND duration IS NULL;   -- 焚音打（【中字4K·HiRes】「焚音打」- M）
UPDATE song SET duration=230 WHERE id=61 AND duration IS NULL;   -- 八芒星ダンス（【Hires·4K60】「 八芒星ダンス）
