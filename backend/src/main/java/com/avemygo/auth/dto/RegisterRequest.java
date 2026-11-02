package com.avemygo.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 注册请求参数。@Valid 校验失败会被 GlobalExceptionHandler 拦下，返回 40001。
 * 字段规则与 docs/06 §2 一致：用户名 3~32 位字母/数字/下划线；密码 6~32 位且含字母和数字。
 */
public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{3,32}$", message = "用户名须为3~32位字母/数字/下划线")
        String username,

        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,32}$", message = "密码须6~32位且同时包含字母和数字")
        String password,

        String nickname   // 可选：不填时默认取用户名
) {
}
