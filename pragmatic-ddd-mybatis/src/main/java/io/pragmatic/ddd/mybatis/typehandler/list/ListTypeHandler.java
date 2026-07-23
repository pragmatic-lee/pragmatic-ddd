package io.pragmatic.ddd.mybatis.typehandler.list;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;

/**
 * 单列 JSON 数组处理器（单例，注册到 List.class）。
 *
 * <p>整列委托共享 {@link JsonSerializer} 序列化/反序列化（元素按运行时类型分派 base/enum/VO 三通道）。
 * 运行期按 MyBatis 传入的<b>列标签</b>（columnLabel）从 {@code columnListTypes} 取对应的 List&lt;E&gt; 参数化类型，
 * 精确还原；多表同名列已由配置期用不同 label 隔离，故此处查表键永远是唯一的列标签。
 *
 * <p>写侧无需元素类型（整列交给 serializer）；读侧必须按 label 取类型，故 label 冲突在配置期已被 fail-fast 拦截。
 */
public class ListTypeHandler implements TypeHandler<List<?>> {

    private final JsonSerializer serializer;
    private final JdbcJsonValue jdbcJsonValue;
    private final Map<String, Type> columnListTypes;   // label -> List<E> 参数化类型
    private final Map<String, ElementConverter> converters;
    private final Type defaultListType;                 // idx/callable 路径兜底（首列类型）

    public ListTypeHandler(JsonSerializer serializer,
                           JdbcJsonValue jdbcJsonValue,
                           Map<String, Type> columnListTypes,
                           Map<String, ElementConverter> converters,
                           Type defaultListType) {
        this.serializer = serializer;
        this.jdbcJsonValue = jdbcJsonValue;
        this.columnListTypes = Map.copyOf(columnListTypes);
        this.converters = Map.copyOf(converters);
        this.defaultListType = defaultListType;
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, List<?> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setNull(i, Types.OTHER);   // JSON 列统一用 Types.OTHER
            return;
        }
        // 整列写成 JSON 数组：每个元素由共享 serializer 按运行时类型分派(base→标量 / enum→code / VO→对象)
        ps.setObject(i, jdbcJsonValue.adapt(serializer.toJsonValue(parameter)));
    }

    @Override
    public List<?> getResult(ResultSet rs, String column) throws SQLException {
        // column = 数据库列标签(JDBC 结果集列标签),即 MyBatis <result column="..."> 值 / SQL AS 别名
        Object raw = rs.getObject(column);
        if (raw == null) {
            return null;
        }
        Type t = columnListTypes.getOrDefault(column, defaultListType);
        List<?> list = serializer.fromJsonValue(raw, t != null ? t : List.class);
        return applyConverters(column, list);
    }

    @Override
    public List<?> getResult(ResultSet rs, int idx) throws SQLException {
        Object raw = rs.getObject(idx);
        if (raw == null) {
            return null;
        }
        return serializer.fromJsonValue(raw, defaultListType != null ? defaultListType : List.class);
    }

    @Override
    public List<?> getResult(CallableStatement cs, int idx) throws SQLException {
        Object raw = cs.getObject(idx);
        if (raw == null) {
            return null;
        }
        return serializer.fromJsonValue(raw, defaultListType != null ? defaultListType : List.class);
    }

    @SuppressWarnings("unchecked")
    private List<?> applyConverters(String column, List<?> list) {
        ElementConverter conv = converters.get(column);
        if (conv == null || conv == ElementConverter.IDENTITY) {
            return list;
        }
        for (int i = 0; i < list.size(); i++) {
            Object e = list.get(i);
            if (e != null) {
                ((List<Object>) list).set(i, conv.convert(e));
            }
        }
        return list;
    }
}
