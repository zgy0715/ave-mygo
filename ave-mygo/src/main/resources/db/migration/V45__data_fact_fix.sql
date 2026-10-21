-- =====================================================================
-- V45__data_fact_fix.sql —— 审计发现的事实/数据错误修正
-- 依据官方来源核实（2026-10-21）：
--   1. 乐队官方写法是 MyGO!!!!!（5 个感叹号），库中误写 4 个
--   2. Ave Mujica 三角初華 是 Gt. & Vo.（吉他手兼主唱），库中只写 Vo.
--   3. Ave Mujica 祐天寺にゃむ 是 Dr.（鼓手），库中误写 Dj.
--   4. 焚音打封面指向 song-3.png，实际文件为 song-3.jpg（404）
-- 全部可重复执行（带条件），不依赖具体 id 的地方用名称匹配。
-- =====================================================================

-- ① 乐队名纠正
UPDATE `band` SET `name` = 'MyGO!!!!!' WHERE `name` = 'MyGO!!!!';

-- ② 成员担当纠正（Ave Mujica）
UPDATE `member` m
    JOIN `band` b ON b.id = m.band_id
SET m.`position` = 'Gt. & Vo.'
WHERE b.`name` = 'Ave Mujica' AND m.`name_ja` = '三角 初華' AND m.`position` = 'Vo.';

UPDATE `member` m
    JOIN `band` b ON b.id = m.band_id
SET m.`position` = 'Dr.'
WHERE b.`name` = 'Ave Mujica' AND m.`name_ja` = '祐天寺 にゃむ' AND m.`position` = 'Dj.';

-- ③ 封面文件名纠正
UPDATE `song` SET `cover_url` = '/img/covers/song-3.jpg'
WHERE `cover_url` = '/img/covers/song-3.png';
