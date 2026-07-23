package io.pragmatic.ddd.mybatis.typehandler.manual;

import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionElementTypeConfig;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionMapping;
import io.pragmatic.ddd.mybatis.typehandler.list.ListTypeHandler;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

import java.util.List;
import java.util.Map;

/**
 * 原生 Java 手动装配演示（零 Spring 依赖）：
 * 手动完成依赖注入与对象组装，并演示"两表同名列、不同类型"的多表映射隔离策略。
 *
 * <p>运行：{@code java io.pragmatic.ddd.mybatis.typehandler.manual.ManualTypeHandlerBootstrap}
 */
public final class ManualTypeHandlerBootstrap {

    // ---- 演示用的领域类型（等价于真实实体/枚举） ----
    enum OrderStatus implements IEnumValue<Integer, OrderStatus> {
        CREATED(1, "已创建"), PAID(2, "已支付");
        private final int value;
        private final String name;

        OrderStatus(int v, String n) {
            this.value = v;
            this.name = n;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    static class Order {
    }

    static class Product {
    }

    static class Address {
        String city;
    }

    public static void main(String[] args) {
        // 1) 手动 DI：装配共享组件（同一份 resolver/serializer，杜绝配置漂移）
        EnumValueResolver resolver = new EnumValueResolver();
        Map<Class<?>, EnumRule> enumRules = Map.of(OrderStatus.class, EnumRule.CODE);
        JsonSerializer serializer = new Fastjson2JsonSerializer(resolver, enumRules);
        JdbcJsonValue jdbcJsonValue = JdbcJsonValue.DEFAULT;

        // 2) 集合字段声明（原生 builder）：两表同字段 tags 但类型不同 -> 用 table 隔离列标签
        List<CollectionMapping> mappings = List.of(
                CollectionMapping.of(Order.class, "tags", String.class).table("o").build(),    // label=o_tags
                CollectionMapping.of(Product.class, "tags", Integer.class).table("p").build(), // label=p_tags
                CollectionMapping.of(Order.class, "addresses", Address.class).build(),         // label=addresses
                CollectionMapping.of(Order.class, "steps", OrderStatus.class).build()          // 枚举, label=steps
        );

        // 3) 构建配置中心：启动期完成枚举预注册 + 同名列冲突 fail-fast
        CollectionElementTypeConfig collections = CollectionElementTypeConfig.from(mappings, resolver);

        // 4) 组装上下文并一次性注册三通道（枚举/JSON/集合）到 SqlSessionFactory
        TypeHandlerContext ctx = new TypeHandlerContext(
                resolver, serializer, jdbcJsonValue, enumRules, List.of(Address.class), collections);
        SqlSessionFactory sqlSessionFactory = new DefaultSqlSessionFactory(new Configuration());
        ctx.registerInto(sqlSessionFactory);

        // 5) 校验：List.class 只注册了单例 ListTypeHandler，且内部按列标签隔离多表类型
        TypeHandlerRegistry reg = sqlSessionFactory.getConfiguration().getTypeHandlerRegistry();
        TypeHandler<?> h = reg.getTypeHandler(List.class);
        ListTypeHandler handler = (ListTypeHandler) h;
        System.out.println("registered ListTypeHandler = " + handler);
        System.out.println("isolated column labels   = " + collections.columnListTypes().keySet());

        // 6) 演示冲突 fail-fast：两表同名列却未隔离 -> 启动期即抛异常
        try {
            CollectionElementTypeConfig.from(List.of(
                    CollectionMapping.of(Order.class, "tags", String.class).columnLabel("tags").build(),
                    CollectionMapping.of(Product.class, "tags", Integer.class).columnLabel("tags").build()
            ), new EnumValueResolver());
            System.out.println("ERROR: 冲突未被拦截!");
        } catch (IllegalStateException ex) {
            System.out.println("OK 冲突已 fail-fast 拦截: " + ex.getMessage());
        }
    }
}
