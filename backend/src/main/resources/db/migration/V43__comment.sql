-- =====================================================================
-- V42__comment.sql —— 歌曲评论
-- 设计：软删除（内容社区留痕），按 (song_id, created_at) 建索引供分页
-- =====================================================================
CREATE TABLE IF NOT EXISTS `comment` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `song_id`    BIGINT UNSIGNED NOT NULL COMMENT '所属歌曲',
    `user_id`    BIGINT UNSIGNED NOT NULL COMMENT '发表用户',
    `content`    VARCHAR(500)    NOT NULL COMMENT '评论内容 1-500 字',
    `is_deleted` TINYINT         NOT NULL DEFAULT 0 COMMENT '软删除',
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_song_created` (`song_id`, `created_at`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='歌曲评论';
