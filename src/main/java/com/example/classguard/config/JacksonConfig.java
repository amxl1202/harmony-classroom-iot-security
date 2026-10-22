package com.example.classguard.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局配置（设计说明书 §4.1 config）：时间字段统一格式
 * yyyy-MM-dd HH:mm:ss（REST 接口文档 §2，服务端时区）。
 *
 * <p>作用范围：响应序列化（LocalDateTime → 字符串）与请求体反序列化（字符串 → LocalDateTime）。
 * URL 查询参数（API-05/07 的 from/to）的绑定格式由 application.properties 的
 * spring.mvc.format.date-time 统一约束，两处格式保持一致。
 */
@Configuration
public class JacksonConfig {

    /** 全局统一时间格式（REST 接口文档 §2） */
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeFormatCustomizer() {
        return builder -> builder
                .serializers(new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .deserializers(new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
    }
}
