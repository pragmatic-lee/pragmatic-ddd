package io.pragmatic.ddd.mybatis.typehandler.json;

import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.base.IValueObject;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Fastjson2JsonSerializer} 行为单测：覆盖四策略往返、嵌套集合/Map、结构化值与原生 JSON 文本还原、null 安全。
 */
class JsonValueObjectSerializerTest {

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

    static class OrderSnapshot implements IValueObject {
        public OrderStatus status;
        public String note;
        public List<OrderStatus> trail;
        public Map<String, OrderStatus> tagged;
    }

    private Fastjson2JsonSerializer serializerFor(EnumRule rule) {
        EnumValueResolver resolver = new EnumValueResolver();
        Map<Class<?>, EnumRule> enumRules = Map.of(OrderStatus.class, rule);
        return new Fastjson2JsonSerializer(resolver, enumRules);
    }

    private OrderSnapshot sample() {
        OrderSnapshot snap = new OrderSnapshot();
        snap.status = OrderStatus.CREATED;
        snap.note = "x";
        snap.trail = List.of(OrderStatus.CREATED, OrderStatus.PAID);
        Map<String, OrderStatus> tagged = new LinkedHashMap<>();
        tagged.put("first", OrderStatus.CREATED);
        snap.tagged = tagged;
        return snap;
    }

    @Test
    void code_rule_roundtrip() {
        Fastjson2JsonSerializer s = serializerFor(EnumRule.CODE);
        OrderSnapshot snap = sample();
        String json = s.serialize(snap);
        assertThat(json).contains("\"status\":1");
        assertThat(json).contains("\"trail\":[1,2]");
        assertThat(json).contains("\"first\":1");

        OrderSnapshot back = s.deserialize(json, OrderSnapshot.class);
        assertThat(back.status).isEqualTo(OrderStatus.CREATED);
        assertThat(back.trail).containsExactly(OrderStatus.CREATED, OrderStatus.PAID);
        assertThat(back.tagged.get("first")).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void name_rule_roundtrip() {
        Fastjson2JsonSerializer s = serializerFor(EnumRule.NAME);
        OrderSnapshot snap = sample();
        String json = s.serialize(snap);
        assertThat(json).contains("\"status\":\"CREATED\"");

        OrderSnapshot back = s.deserialize(json, OrderSnapshot.class);
        assertThat(back.status).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void label_rule_roundtrip() {
        Fastjson2JsonSerializer s = serializerFor(EnumRule.LABEL);
        OrderSnapshot snap = sample();
        String json = s.serialize(snap);
        assertThat(json).contains("\"status\":\"已创建\"");

        OrderSnapshot back = s.deserialize(json, OrderSnapshot.class);
        assertThat(back.status).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void ordinal_rule_roundtrip() {
        Fastjson2JsonSerializer s = serializerFor(EnumRule.ORDINAL);
        OrderSnapshot snap = sample();
        // CREATED.ordinal() == 0, PAID.ordinal() == 1
        String json = s.serialize(snap);
        assertThat(json).contains("\"status\":0");
        assertThat(json).contains("\"trail\":[0,1]");

        OrderSnapshot back = s.deserialize(json, OrderSnapshot.class);
        assertThat(back.status).isEqualTo(OrderStatus.CREATED);
        assertThat(back.trail).containsExactly(OrderStatus.CREATED, OrderStatus.PAID);
    }

    @Test
    void toJsonValue_produces_structured_object() {
        Fastjson2JsonSerializer s = serializerFor(EnumRule.CODE);
        Object structured = s.toJsonValue(sample());
        assertThat(structured).isInstanceOf(com.alibaba.fastjson2.JSONObject.class);

        // 结构化值再经 fromJsonValue 还原，与 deserialize 等价
        OrderSnapshot back = s.fromJsonValue(structured, OrderSnapshot.class);
        assertThat(back.status).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void fromJsonValue_accepts_raw_string() {
        Fastjson2JsonSerializer s = serializerFor(EnumRule.CODE);
        OrderSnapshot back = s.fromJsonValue("{\"status\":2,\"note\":\"y\"}", OrderSnapshot.class);
        assertThat(back.status).isEqualTo(OrderStatus.PAID);
        assertThat(back.note).isEqualTo("y");
    }

    @Test
    void null_safe() {
        Fastjson2JsonSerializer s = serializerFor(EnumRule.CODE);
        assertThat(s.serialize(null)).isNull();
        assertThat(s.deserialize(null, OrderSnapshot.class)).isNull();
        assertThat(s.deserialize("", OrderSnapshot.class)).isNull();
        assertThat(s.toJsonValue(null)).isNull();
        assertThat(s.fromJsonValue(null, OrderSnapshot.class)).isNull();
    }
}
