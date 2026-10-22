package com.example.classguard.config;

import com.example.classguard.dto.ApiResponse;
import com.example.classguard.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 全局异常处理器（设计说明书 §4.5）：统一按 {code, message, data} 返回，避免堆栈信息外泄。
 * <p>业务代码通过 throw new BusinessException(ErrorCode.XXX) 返回业务错误码；
 * 拦截器 preHandle 中抛出的异常同样由本处理器承接并转为 JSON 响应（2001/2003）。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常 → 对应错误码（1002/1003/1004/2001/2003/3001/3002 等）。
     * <p>3002 设备指令超时（REST v1.1 API-09 超时响应）时 data 携带 cmdId
     * （形如 {"cmdId":"cmd-20260915-0001","result":null,"reason":null,"state":null}），
     * 其余业务异常 data 为 null；统一经 {@link ApiResponse#error(int, String, Object)} 输出。
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Object> handleBusiness(BusinessException e) {
        log.warn("业务异常：code={}, message={}, data={}", e.getCode(), e.getMessage(), e.getData());
        return ApiResponse.error(e.getCode(), e.getMessage(), e.getData());
    }

    /** @Valid 参数校验失败 → 1001 参数错误（返回第一条校验提示） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage() == null ? "参数错误" : f.getDefaultMessage())
                .orElse("参数错误");
        return ApiResponse.error(1001, message);
    }

    /** 查询参数类型/格式错误（如 from/to 不符合 yyyy-MM-dd HH:mm:ss）→ 1001 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ApiResponse<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ApiResponse.error(1001, "参数格式错误：" + e.getName());
    }

    /** 请求体 JSON 无法解析 → 1001 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return ApiResponse.error(1001, "请求体格式错误");
    }

    /** 未捕获异常 → 5000 服务器内部错误（详情仅记日志） */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("服务器内部错误", e);
        return ApiResponse.error(5000, "服务器内部错误");
    }
}
