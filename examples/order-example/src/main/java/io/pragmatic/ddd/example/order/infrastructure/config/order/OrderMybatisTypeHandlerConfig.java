package io.pragmatic.ddd.example.order.infrastructure.config.order;

import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentStatus;
import io.pragmatic.ddd.example.order.domain.order.model.enums.ShipmentStatus;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.LogisticsInfo;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.PaymentInfo;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionElementTypeConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 订单专属 MyBatis 复杂类型 TypeHandler 装配：产出仅登记订单聚合枚举与值对象的
 * {@link TypeHandlerContext} Bean，与其它聚合的 TypeHandlerContext 一起由通用
 * {@code config.MySqlConfig} 在会话工厂构建阶段逐个灌入原生 Configuration（经
 * {@code List<TypeHandlerContext>} 聚合注入）。本类不建数据源/会话工厂，也不做连接相关配置。
 *
 * @author wizard-lee
 */
@Configuration
public class OrderMybatisTypeHandlerConfig {

    @Bean
    public TypeHandlerContext orderTypeHandlerContext() {
        EnumValueResolver resolver = new EnumValueResolver();
        Map<Class<?>, EnumRule> enumRules = Map.of(
                OrderStatus.class, EnumRule.CODE,
                PaymentStatus.class, EnumRule.CODE,
                ShipmentStatus.class, EnumRule.CODE,
                PaymentMethod.class, EnumRule.CODE);
        List<Class<?>> voTypes = List.of(
                Customer.class, Address.class, Money.class,
                PaymentInfo.class, LogisticsInfo.class);
        return new TypeHandlerContext(
                resolver,
                new Fastjson2JsonSerializer(resolver, enumRules),
                JdbcJsonValue.MYSQL,
                enumRules,
                voTypes,
                CollectionElementTypeConfig.empty());
    }
}
