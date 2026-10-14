package io.pragmatic.ddd.example.order;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 订单示例应用启动类。
 * 引入 pragmatic-ddd-spring-boot starter，事务 / Outbox / 事件总线 / ID 号段 / 补偿 / 生命周期
 * 等 DDD 基础设施由其自动装配；数据源 / SqlSessionFactory / Redis / Elasticsearch 等
 * starter 不覆盖的连接类 Bean 仍由 infrastructure/config 下的手写配置显式提供。
 * 保留 {@link DataSourceAutoConfiguration} 排除：数据源由 MySqlConfig 手工构建（需注入 TypeHandler 与 Mapper XML）。
 *
 * @author wizard-lee
 */
@Slf4j
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class
},scanBasePackages = "io.pragmatic.ddd.example.order")
public class AppStart {

    public static void main(String[] args) {
        try{

            SpringApplication.run(AppStart.class, args);
        }
        catch (Exception ex){
            log.error("xx",ex);
        }
    }
}
