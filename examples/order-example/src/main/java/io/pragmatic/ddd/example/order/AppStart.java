package io.pragmatic.ddd.example.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 订单示例应用启动类。
 * 本示例引入 Spring Boot 作为 IOC 容器与运行器，但不启用其自动装配，
 * 所有 Bean（DataSource / RedisTemplate / ElasticsearchClient / RocketMqEventManager 等）均由
 * infrastructure/order/config 下的手写配置类显式提供。
 *
 * @author wizard-lee
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class
},scanBasePackages = "io.pragmatic.ddd.example.order")
public class AppStart {

    public static void main(String[] args) {
        SpringApplication.run(AppStart.class, args);
    }
}
