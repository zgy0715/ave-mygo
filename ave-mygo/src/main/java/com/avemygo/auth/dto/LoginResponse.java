package com.avemygo.auth.dto;

/** 登录成功响应：token + 有效期（秒）+ 用户基本信息 */
public record LoginResponse(
        String token,
        long expiresIn,
        UserBrief user
) {
    public record UserBrief(Long userId, String username, String nickname, String role) {
    }
}
