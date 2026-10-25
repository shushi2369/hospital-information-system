package com.his.infrastructure.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 序列化约定（《03》§1.1）：金额以字符串传输，避免 JS 浮点精度丢失。
 * LocalDateTime 统一输出 yyyy-MM-dd HH:mm:ss（一百四十六轮 F-C4：原 ISO 带 T 直出，
 * 收费时间/开单时间等列表展示不符合阅读习惯；仅改序列化，前端提交的 ISO 反序列化不受影响）。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalAsString() {
        return builder -> builder
                .serializerByType(BigDecimal.class, ToStringSerializer.instance)
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATETIME));
    }
}
