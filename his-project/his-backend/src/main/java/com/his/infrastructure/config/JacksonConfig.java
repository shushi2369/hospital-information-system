package com.his.infrastructure.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * 序列化约定（《03》§1.1）：金额以字符串传输，避免 JS 浮点精度丢失。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalAsString() {
        return builder -> builder.serializerByType(BigDecimal.class, ToStringSerializer.instance);
    }
}
