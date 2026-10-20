-- =====================================================================
-- V39__ep_to_bvid.sql —— MV 从番剧 ep_id 内嵌改为 bvid 播放器 / 番剧跳转卡
-- 背景：B 站禁止第三方 iframe 嵌入番剧页，ep_id 形式必然播放失败（用户截图确认）。
-- 原则：优先可用 bvid（官方投稿 > 完整音频搬运）；无源者改番剧页跳转卡（BILIBILI_PAGE）。
-- 全部 bvid 经 B 站 view API 核验（标题/UP/时长），非猜测。
-- 幂等：纯 UPDATE，可重复执行。
-- =====================================================================

-- 栞：Official PV 补档（杜比全景声+HIRES，4:32）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1V23TzsERt&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=6;
-- 碧天伴走：6th LIVE 中字 4K HiRes（3:56）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1ACJHzAExV&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=11;
-- 顔：4K Hi-Res 中日双语（4:00）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1EjqzBvEms&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=16;
-- 聿日箋秋：HiRes 4K60（3:57）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1raZZYJE1D&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=27;
-- KiLLKiSS：中日歌词 正式完整版 无损（3:30）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1wnrDYoEGY&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=49;
-- Georgette Me, Georgette You：4K Hi-Res 完整版（3:55）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1L9cteeERx&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=50;
-- Crucifix X：Music Video（5:00）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1SYQ6YPEmZ&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=52;
-- 天球(そら)のMúsica：官方账号 MyGO_AveMujica 投稿 Official MV（4:41）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1RtZmYGE3a&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=53;
-- 焚音打：7th LIVE 中字 4K HiRes（5:32）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV16dV3zDEZX&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=60;
-- 八芒星ダンス：Hires 4K60（3:50）
UPDATE song SET `mv_url`='https://player.bilibili.com/player.html?bvid=BV1n4MjzEEwx&autoplay=0', `mv_platform`='BILIBILI' WHERE `id`=61;

-- 无 bvid 源：改为番剧页跳转卡（前端对非 BILIBILI 平台渲染外链按钮）
UPDATE song SET `mv_url`='https://www.bilibili.com/bangumi/play/ep1521592', `mv_platform`='BILIBILI_PAGE' WHERE `id`=51;
UPDATE song SET `mv_url`='https://www.bilibili.com/bangumi/play/ep1456209', `mv_platform`='BILIBILI_PAGE' WHERE `id`=62;
