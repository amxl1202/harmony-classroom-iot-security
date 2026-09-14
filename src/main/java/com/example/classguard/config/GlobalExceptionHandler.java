package com.example.classguard.config;

import com.example.classguard.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（设计说明书 §4.5）：统一按 {code, message, data} 返回，避免堆栈信息外泄。
 * <p>业务错误码（1002/1003/1004/2001/2003/3001/3002）后续通过自定义业务异常类承接，
 * 届时在此补充对应 @ExceptionHandler。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** @Valid 参数校验失败 → 1001 参数错误（返回第一条校验提示） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage() == null ? "参数错误" : f.getDefaultMessage())
                .orElse("参数错误");
        return ApiResponse.error(1001, message);
    }

    /** 未捕获异常 → 5000 服务器内部错误（详情仅记日志） */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return ApiResponse.error(5000, "服务器内部错误");
    }
}
