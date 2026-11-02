-- =====================================================================
-- V1__init.sql —— Bandori Live 初始表结构
-- 依据：docs/05-数据库设计.md v1.0
-- 约定：utf8mb4 / InnoDB；不建物理外键（应用层保证）；金额用 DECIMAL
-- =====================================================================

-- 1. 用户表
CREATE TABLE `user` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `username`      VARCHAR(32)     NOT NULL COMMENT '登录名，3~32位字母/数字/下划线',
    `password_hash` VARCHAR(100)    NOT NULL COMMENT 'BCrypt 密文',
    `nickname`      VARCHAR(32)     NOT NULL COMMENT '展示昵称',
    `role`          VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT 'USER / ADMIN',
    `status`        TINYINT         NOT NULL DEFAULT 1 COMMENT '1正常 0封禁',
    `avatar_url`    VARCHAR(255)    NULL,
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`    TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户';

-- 2. 乐队表
CREATE TABLE `band` (
    `id`          SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name`        VARCHAR(50)       NOT NULL COMMENT '乐队名，如 MyGO!!!!',
    `slug`        VARCHAR(50)       NOT NULL COMMENT 'URL 标识',
    `description` TEXT              NULL,
    `logo_url`    VARCHAR(255)      NULL,
    `created_at`  DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slug` (`slug`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '乐队';

-- 3. 专辑/单曲表
CREATE TABLE `album` (
    `id`           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    `band_id`      SMALLINT UNSIGNED NOT NULL COMMENT '逻辑FK->band',
    `name`         VARCHAR(100)     NOT NULL,
    `type`         VARCHAR(20)      NOT NULL COMMENT 'SINGLE/EP/ALBUM/MINI_ALBUM',
    `cover_url`    VARCHAR(255)     NULL,
    `release_date` DATE             NULL,
    `created_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_band` (`band_id`),
    KEY `idx_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '专辑/单曲';

-- 4. 成员表
CREATE TABLE `member` (
    `id`          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    `band_id`     SMALLINT UNSIGNED NOT NULL COMMENT '逻辑FK->band',
    `name_ja`     VARCHAR(50)       NOT NULL COMMENT '官方日文名',
    `name_romaji` VARCHAR(50)       NOT NULL COMMENT '罗马字',
    `cv`          VARCHAR(50)       NULL COMMENT '声优',
    `position`    VARCHAR(20)       NULL COMMENT 'Vo./Gt./Ba./Dr./Key.',
    `avatar_url`  VARCHAR(255)      NULL,
    `intro`       TEXT              NULL,
    `sort`        INT               NOT NULL DEFAULT 0,
    `created_at`  DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_band` (`band_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '乐队成员';

-- 5. 歌曲表
CREATE TABLE `song` (
    `id`           BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    `title`        VARCHAR(100)      NOT NULL,
    `band_id`      SMALLINT UNSIGNED NOT NULL COMMENT '逻辑FK->band',
    `album_id`     BIGINT UNSIGNED   NULL COMMENT '逻辑FK->album',
    `track_no`     INT               NULL COMMENT '专辑内曲目序号',
    `release_date` DATE              NULL,
    `duration`     INT               NULL COMMENT '时长（秒）',
    `cover_url`    VARCHAR(255)      NULL,
    `audio_url`    VARCHAR(255)      NULL COMMENT '试听音源外链，不托管音频',
    `mv_url`       VARCHAR(255)      NULL COMMENT '官方 MV 链接',
    `mv_platform`  VARCHAR(20)       NULL COMMENT 'BILIBILI / YOUTUBE',
    `tags`         VARCHAR(200)      NULL COMMENT '逗号分隔，如 燃,夜勤',
    `status`       TINYINT           NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    `created_at`   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_band` (`band_id`),
    KEY `idx_album` (`album_id`),
    KEY `idx_title` (`title`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '歌曲';

-- 6. 演出表
CREATE TABLE `live_event` (
    `id`          BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
    `title`       VARCHAR(100)      NOT NULL,
    `band_id`     SMALLINT UNSIGNED NOT NULL COMMENT '逻辑FK->band',
    `description` TEXT              NULL,
    `poster_url`  VARCHAR(255)      NULL,
    `venue`       VARCHAR(100)      NULL COMMENT '场馆',
    `status`      VARCHAR(20)       NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ENDED/CANCELLED',
    `created_at`  DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME          NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`  TINYINT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_band` (`band_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '演出';

-- 7. 场次表
CREATE TABLE `live_session` (
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `event_id`        BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->live_event',
    `name`            VARCHAR(50)     NOT NULL COMMENT '如 昼场/夜场',
    `start_time`      DATETIME        NOT NULL COMMENT '开演时间',
    `end_time`        DATETIME        NOT NULL COMMENT '预计散场时间',
    `sale_start_time` DATETIME        NOT NULL COMMENT '开售时间',
    `status`          VARCHAR(20)     NOT NULL DEFAULT 'NOT_OPEN' COMMENT 'NOT_OPEN/ONSALE/ENDED；SOLD_OUT为派生不落库',
    `created_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_event` (`event_id`),
    KEY `idx_sale_start` (`sale_start_time`),
    KEY `idx_end` (`end_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '演出场次';

-- 8. 票档表 —— 防超卖核心表
CREATE TABLE `ticket_type` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `session_id`  BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->live_session',
    `name`        VARCHAR(50)     NOT NULL COMMENT '如 S席/普通席',
    `price`       DECIMAL(10,2)   NOT NULL COMMENT '单价（元）',
    `total_stock` INT UNSIGNED    NOT NULL COMMENT '总库存',
    `sold_count`  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '已售数；available=total-sold 派生',
    `max_per_user` TINYINT UNSIGNED NOT NULL DEFAULT 4 COMMENT '单人限购',
    `status`      TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_session` (`session_id`),
    CONSTRAINT `chk_stock` CHECK (`sold_count` <= `total_stock`)  -- 防超卖最后一道防线
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '票档';

-- 9. 订单表 —— 状态机核心表（无 is_deleted：只流转不删除）
CREATE TABLE `ticket_order` (
    `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no`      VARCHAR(32)     NOT NULL COMMENT 'BL+yyyyMMdd+10位随机',
    `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->user',
    `event_id`      BIGINT UNSIGNED NOT NULL COMMENT '冗余，便于列表筛选',
    `session_id`    BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->live_session',
    `ticket_type_id` BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->ticket_type',
    `quantity`      TINYINT UNSIGNED NOT NULL COMMENT '1~10',
    `unit_price`    DECIMAL(10,2)   NOT NULL COMMENT '下单时价格快照',
    `total_amount`  DECIMAL(10,2)   NOT NULL COMMENT 'unit_price*quantity',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT 'PENDING_PAYMENT/PAID/CANCELLED/EXPIRED/REFUNDED',
    `pay_deadline`  DATETIME        NOT NULL COMMENT 'created_at+15min，超时任务扫描依据',
    `paid_at`       DATETIME        NULL,
    `cancelled_at`  DATETIME        NULL COMMENT '用户取消/超时共用',
    `refunded_at`   DATETIME        NULL,
    `refund_reason` VARCHAR(200)    NULL COMMENT '管理员退款必填',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_user_status` (`user_id`, `status`),
    KEY `idx_session` (`session_id`),
    KEY `idx_pay_deadline` (`pay_deadline`),
    KEY `idx_event` (`event_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '购票订单';

-- 10. 收藏表
CREATE TABLE `favorite` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->user',
    `target_type` VARCHAR(20)     NOT NULL COMMENT 'SONG / EVENT',
    `target_id`   BIGINT UNSIGNED NOT NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_target` (`user_id`, `target_type`, `target_id`),
    KEY `idx_target` (`target_type`, `target_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏';

-- 11. 歌单表
CREATE TABLE `playlist` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->user',
    `name`        VARCHAR(50)     NOT NULL,
    `description` VARCHAR(200)    NULL,
    `created_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '歌单';

-- 12. 歌单曲目表
CREATE TABLE `playlist_item` (
    `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `playlist_id` BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->playlist',
    `song_id`     BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->song',
    `sort`        INT             NOT NULL DEFAULT 0,
    `added_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_playlist_song` (`playlist_id`, `song_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '歌单曲目';

-- 13. 演出曲表（P2 预留，一次建齐）
CREATE TABLE `setlist_item` (
    `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `session_id` BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->live_session',
    `song_id`    BIGINT UNSIGNED NOT NULL COMMENT '逻辑FK->song',
    `order_no`   INT             NOT NULL COMMENT '曲目顺序',
    `is_encore`  TINYINT         NOT NULL DEFAULT 0,
    `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_order` (`session_id`, `order_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '演出曲表';

-- ============ 种子数据：两支乐队 ============
INSERT INTO `band` (`id`, `name`, `slug`, `description`) VALUES
(1, 'MyGO!!!!', 'mygo', '失落与寻找的乐队。成员：高松灯、千早爱音、要乐奈、长崎素世、椎名立希。'),
(2, 'Ave Mujica', 'ave-mujica', '面具下的完美人偶剧团。成员：三角初华、若叶睦、八幡海铃、祐天寺若麦、丰川祥子。');
