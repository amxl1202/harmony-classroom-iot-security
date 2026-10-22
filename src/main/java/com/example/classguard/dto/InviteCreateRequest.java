package com.example.classguard.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * API-10 生成邀请码请求体（REST v1.2 API-10，管理接口，需管理员 JWT）。
 * <p>请求体整体可选：不传或传空对象时 validDays 取缺省值 7。
 */
@Data
public class InviteCreateRequest {

    /** 有效期天数，可选，缺省 7；范围 1~30，越界返回 1001（REST v1.2 API-10） */
    @Min(value = 1, message = "validDays 须为 1~30")
    @Max(value = 30, message = "validDays 须为 1~30")
    private Integer validDays;
}
