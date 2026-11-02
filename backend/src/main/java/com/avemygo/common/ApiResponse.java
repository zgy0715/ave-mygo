package com.avemygo.common;

/**
 * 统一响应结构（docs/02 开发规范）：{code, message, data}
 * 所有接口的返回值都用它包裹。
 */
public record ApiResponse<T>(int code, String message, T data) {

    /** 成功（带数据） */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data);
    }

    /** 成功（无数据） */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "ok", null);
    }

    /** 业务失败：code 对应 docs/06 错误码表 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
