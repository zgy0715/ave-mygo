-- =====================================================================
-- V19__sumimi_covers.sql —— sumimi 封面落位 + 专辑归属
-- 素材：官方配信封面 sumimi-here-the-world.jpg（Sweet Escape 与
-- Here, the world! 共用——BD 特典双 A 面，无 Sweet Escape 独立 jacket）。
-- =====================================================================

INSERT INTO album (`id`, `band_id`, `name`, `type`, `release_date`, `cover_url`) VALUES
(12, 3, 'Here, the world!', 'SINGLE', '2025-05-28', '/img/covers/sumimi-here-the-world.jpg');

UPDATE song SET `album_id` = 12, `cover_url` = '/img/covers/sumimi-here-the-world.jpg' WHERE `id` = 58;   -- Here, the world!
UPDATE song SET `album_id` = 12, `cover_url` = '/img/covers/sumimi-here-the-world.jpg' WHERE `id` = 59;   -- Sweet Escape
