-- V55: 封禁即时生效（审阅项 3）
--
-- 问题：JwtAuthFilter 只验签名与过期，不查账户状态。
-- 管理员封禁用户后，对方手里的 token 在过期前（默认 24 小时）仍然有效。
--
-- 方案：用户表加 token_version，签发时写进令牌载荷；
--       封禁/降权时 +1，过滤器比对版本号不一致即视为失效（无需维护黑名单）。
--       之后再改角色也能靠同一字段强制下线。

ALTER TABLE `user`
    ADD COLUMN `token_version` INT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '令牌版本：封禁/权限变更时 +1，旧令牌立即失效';
