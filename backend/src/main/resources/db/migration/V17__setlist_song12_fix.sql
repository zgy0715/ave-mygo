-- =====================================================================
-- V17__setlist_song12_fix.sql —— 清理《忘れてやまない》(song id=12) 删除后的悬空引用
-- 依据：V16__official_covers 核对结论——该曲在整个 BanG Dream! 企划中不存在。
-- 昼场 order 3 引用了 song 12 → 删除该行，后续曲目前移补位。
-- =====================================================================

DELETE FROM `setlist_item` WHERE `session_id` = 1 AND `song_id` = 12;
UPDATE `setlist_item` SET `order_no` = `order_no` - 1 WHERE `session_id` = 1 AND `order_no` > 3;
