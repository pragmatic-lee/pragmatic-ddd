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
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerDeclaration;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 订单专属复杂类型声明：只声明订单聚合涉及的枚举与值对象，不构建解析器/序列化器等运行时组件。
 * 框架侧（{@code DddMybatisTypeHandlerAutoConfiguration}）会收集容器内全部声明，
 * 合并为唯一的 {@code TypeHandlerContext} 后注册到 MyBatis，避免多聚合各自注册互相覆盖。
 * 本类不建数据源/会话工厂，也不做连接相关配置。
 *
 * @author wizard-lee
 */
@Configuration
public class OrderMybatisTypeHandlerConfig {

    @Bean
    public TypeHandlerDeclaration orderTypeHandlerDeclaration() {
        Map<Class<?>, EnumRule> enumRules = Map.of(
                OrderStatus.class, EnumRule.CODE,
                PaymentStatus.class, EnumRule.CODE,
                ShipmentStatus.class, EnumRule.CODE,
                PaymentMethod.class, EnumRule.CODE);
        List<Class<?>> voTypes = List.of(
                Customer.class, Address.class, Money.class,
                PaymentInfo.class, LogisticsInfo.class);
        return TypeHandlerDeclaration.of(enumRules, voTypes, List.of());
    }
}
