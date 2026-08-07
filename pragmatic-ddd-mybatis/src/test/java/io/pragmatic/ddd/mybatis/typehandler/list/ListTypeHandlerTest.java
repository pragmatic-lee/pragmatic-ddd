package io.pragmatic.ddd.mybatis.typehandler.list;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.support.JdbcMocks;
import io.pragmatic.ddd.mybatis.support.JdbcMocks.PsRecorder;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ListTypeHandler 纯单测：集合读路径（还原、converter、NULL）与 NULL 写路径。
 * 说明：setParameter 内部经 serializer.toJsonValue 写入，而 toJsonValue 对集合/数组顶层
 * 按 JSONObject.class 强转会抛 JSONException（Fastjson2JsonSerializer 的已知限制，属主代码范畴），
 * 故写路径的集合序列化不在本次测试范围内，仅覆盖稳定的读取路径。
 *
 * @author wizard-lee
 */
@DisplayName("ListTypeHandler JDBC 绑定")
class ListTypeHandlerTest {

    static class Post {
    }

    private final JsonSerializer serializer =
            new Fastjson2JsonSerializer(new EnumValueResolver(), Map.of());

    private CollectionElementTypeConfig config() {
        return CollectionElementTypeConfig.from(
                List.of(CollectionMapping.of(Post.class, "tags", String.class).build()),
                new EnumValueResolver());
    }

    private ListTypeHandler handler(CollectionElementTypeConfig config) {
        Map<String, Type> columnListTypes = config.columnListTypes();
        return new ListTypeHandler(
                serializer,
                JdbcJsonValue.DEFAULT,
                columnListTypes,
                config.converters(),
                columnListTypes.get("tags"));
    }

    @Test
    @DisplayName("setParameter 传 null 时统一写为 Types.OTHER")
    void setParameterWritesNull() throws Exception {
        ListTypeHandler handler = handler(config());
        PsRecorder recorder = new PsRecorder();

        handler.setParameter(JdbcMocks.fakePreparedStatement(recorder), 2, null, JdbcType.VARCHAR);

        assertThat(recorder.nullType(2)).isEqualTo(Types.OTHER);
    }

    @Test
    @DisplayName("getResult 按列标签还原集合")
    void getResultRestoresList() throws Exception {
        ListTypeHandler handler = handler(config());
        String raw = serializer.serialize(List.of("a", "b"));
        ResultSet rs = JdbcMocks.fakeResultSetWithColumn("tags", raw, false);

        List<?> result = handler.getResult(rs, "tags");

        assertThat(result).isEqualTo(List.of("a", "b"));
    }

    @Test
    @DisplayName("getResult 数据库 NULL 还原为 null")
    void getResultNull() throws Exception {
        ListTypeHandler handler = handler(config());
        ResultSet rs = JdbcMocks.fakeResultSetWithColumn("tags", null, true);

        assertThat(handler.getResult(rs, "tags")).isNull();
    }

    @Test
    @DisplayName("getResult 应用元素级 converter")
    void getResultAppliesConverter() throws Exception {
        CollectionElementTypeConfig upperConfig = CollectionElementTypeConfig.from(
                List.of(CollectionMapping.of(Post.class, "tags", String.class)
                        .converter(e -> e.toString().toUpperCase())
                        .build()),
                new EnumValueResolver());
        ListTypeHandler upperHandler = handler(upperConfig);
        String raw = serializer.serialize(List.of("a", "b"));
        ResultSet rs = JdbcMocks.fakeResultSetWithColumn("tags", raw, false);

        List<?> result = upperHandler.getResult(rs, "tags");

        assertThat(result).isEqualTo(List.of("A", "B"));
    }
}
