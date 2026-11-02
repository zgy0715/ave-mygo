package com.avemygo.common;

import lombok.Getter;

/**
 * 业务异常：业务规则不满足时抛出。
 * code 取值见 docs/06-接口设计.md 错误码表（如 40901 库存不足）。
 * 用法：throw new BizException(40904, "用户名已存在");
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
