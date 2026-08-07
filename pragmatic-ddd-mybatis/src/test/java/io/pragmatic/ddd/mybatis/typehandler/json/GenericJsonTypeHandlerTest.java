package io.pragmatic.ddd.mybatis.typehandler.json;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.support.JdbcMocks;
import io.pragmatic.ddd.mybatis.support.JdbcMocks.PsRecorder;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import org.apache.ibatis.type.JdbcType;

import java.sql.ResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GenericJsonTypeHandler 纯单测：setParameter 写结构化 JSON 值，getResult 还原对象。
 *
 * @author wizard-lee
 */
@DisplayName("GenericJsonTypeHandler JDBC 绑定")
class GenericJsonTypeHandlerTest {

    public static class Person {
        public String name;
        public int age;

        public Person() {
        }

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    private final JsonSerializer serializer =
            new Fastjson2JsonSerializer(new EnumValueResolver(), Map.of());
    private final GenericJsonTypeHandler<Person> handler =
            new GenericJsonTypeHandler<>(Person.class, serializer, JdbcJsonValue.DEFAULT);

    @Test
    @DisplayName("setParameter 将对象写为结构化 JSON 值")
    void setParameterWritesJsonValue() throws Exception {
        PsRecorder recorder = new PsRecorder();
        Person person = new Person("neo", 18);

        handler.setParameter(JdbcMocks.fakePreparedStatement(recorder), 1, person, JdbcType.OTHER);

        assertThat(recorder.objectParam(1)).isEqualTo(serializer.toJsonValue(person));
    }

    @Test
    @DisplayName("setParameter 传 null 时写为 NULL")
    void setParameterWritesNull() throws Exception {
        PsRecorder recorder = new PsRecorder();
        handler.setParameter(JdbcMocks.fakePreparedStatement(recorder), 2, null, JdbcType.OTHER);

        assertThat(recorder.nullType(2)).isEqualTo(JdbcType.OTHER.TYPE_CODE);
    }

    @Test
    @DisplayName("getResult 从结构化 JSON 值还原对象")
    void getResultRestoresObject() throws Exception {
        Person person = new Person("neo", 18);
        ResultSet rs = JdbcMocks.fakeResultSetWithColumn("data", serializer.toJsonValue(person), false);

        Person result = handler.getResult(rs, "data");

        assertThat(result.name).isEqualTo("neo");
        assertThat(result.age).isEqualTo(18);
    }

    @Test
    @DisplayName("getResult 数据库 NULL 还原为 null")
    void getResultNull() throws Exception {
        ResultSet rs = JdbcMocks.fakeResultSetWithColumn("data", null, true);

        assertThat(handler.getResult(rs, "data")).isNull();
    }
}
