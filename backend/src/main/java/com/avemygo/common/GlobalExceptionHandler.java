package com.avemygo.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：所有 Controller 抛出的异常在此统一翻译成
 * {"code": xxx, "message": "...", "data": null}，业务代码里不再出现 try-catch。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：code/message 由抛出方决定（40904 用户名已存在、40901 库存不足…） */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    /** 参数校验失败（@Valid 触发）：40001 + 具体字段信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数错误");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(40001, detail));
    }

    /** 兜底：未知异常。堆栈只进日志，不泄露给前端 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(50001, "服务开小差了，请稍后重试"));
    }
}
