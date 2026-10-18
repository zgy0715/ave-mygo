-- =====================================================================
-- V11__discography_expansion.sql —— 全曲库扩充（数据源：站长提供的官方曲绘目录）
-- 已知发行信息的曲目沿用；未知日期/时长的留空，由封面图入库时一并补齐。
-- 同时创建 sumimi 乐队（にゃむ × 初華 的双人组合）。
-- =====================================================================

-- ---------- sumimi 乐队 ----------
INSERT INTO band (`id`, `name`, `slug`, `description`) VALUES
(3, 'sumimi', 'sumimi', '祐天寺にゃむ × 純田まな 的双人偶像组合。镜头前的闪闪发光，是两个人一起挣来的。');

-- ---------- MyGO!!!!! 新增曲目 (band 1) ----------
INSERT INTO song (`id`, `title`, `band_id`, `status`) VALUES
(17, '迷跡波', 1, 1),
(18, '迷路日々', 1, 1),
(19, '処救生', 1, 1),
(20, '輪符雨', 1, 1),
(21, '砂寸奏／回層浮', 1, 1),
(22, '端程山', 1, 1),
(23, '過惰幻', 1, 1),
(24, '霧周途', 1, 1),
(25, '歩拾道', 1, 1),
(26, '跡暖空', 1, 1),
(27, '聿日箋秋', 1, 1),
(28, '残痕字 (TV Size)', 1, 1),
(29, '往欄印', 1, 1),
(30, 'エガクミライ', 1, 1),
(31, '静降想', 1, 1),
(32, '証命讃歌', 1, 1),
(33, '過去を喰らう (Cover)', 1, 1),
(34, 'だれかの心臓になれたなら (Cover)', 1, 1),
(35, 'ノンブレス·オブリージュ (Cover)', 1, 1),
(36, 'バンドリ!カバーコレクション', 1, 1),
(37, '潜在表明 - From THE FIRST TAKE', 1, 1);

-- ---------- Ave Mujica 新增曲目 (band 2) ----------
INSERT INTO song (`id`, `title`, `band_id`, `status`) VALUES
(38, 'ふたつの月 ~Deep Into The Forest~', 2, 1),
(39, 'Choir ''S'' Choir', 2, 1),
(40, '神さま、バカ', 2, 1),
(41, 'Masquerade Rhapsody Request', 2, 1),
(42, 'Angles', 2, 1),
(43, 'Symbol I', 2, 1),
(44, 'Symbol II Air', 2, 1),
(45, 'Symbol III', 2, 1),
(46, 'Symbol IV Earth', 2, 1),
(47, '素晴らしき世界 でも どこにもない場所', 2, 1),
(48, 'ELEMENTS', 2, 1),
(49, 'KiLLKiSS', 2, 1),
(50, 'Georgette Me, Georgette You', 2, 1),
(51, 'Imprisoned XII', 2, 1),
(52, 'Crucifix X', 2, 1),
(53, '天球(そら)のMúsica', 2, 1),
(54, 'DIVINE', 2, 1),
(55, 'in your blue eyes', 2, 1),
(56, 'S The Way', 2, 1),
(57, 'Completeness', 2, 1);

-- ---------- sumimi 成员（にゃむ × 純田まな） ----------
INSERT INTO member (`id`, `band_id`, `name_ja`, `name_romaji`, `cv`, `position`, `intro`, `sort`) VALUES
(11, 3, '祐天寺 にゃむ', 'Yutenji Nyamu', '米澤茜', 'Perf.', 'sumimi 的发起人。把偶像活动当作自媒体流量的延长线。', 1),
(12, 3, '純田 まな',   'Mana',          '反田叶月', 'Vo.',  'sumimi 的主唱。元气满满的偶像担当，永远站在镜头最前面。', 2);
