package io.pragmatic.ddd.example.order.api.common;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局时间序列化格式（对齐契约 3.3）：
 * LocalDateTime 输出 yyyy-MM-dd HH:mm:ss，LocalDate 输出 yyyy-MM-dd。
 * 响应 DTO 字段另以 @JsonFormat 兜底，保证格式不随配置漂移。
 *
 * @author wizard-lee
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonDateTimeCustomizer() {
        return builder -> builder
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER))
                .serializerByType(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
    }
}
