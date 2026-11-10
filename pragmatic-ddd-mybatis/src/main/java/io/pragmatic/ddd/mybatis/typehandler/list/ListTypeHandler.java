package io.pragmatic.ddd.mybatis.typehandler.list;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.lang.reflect.Type;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 单列 JSON 数组处理器（全局单例，注册到 List.class）：整列委托共享 {@link JsonSerializer}，严格按列标签还原元素类型。
 *
 * <p>列标签必须已在集合声明中登记，未登记直接抛错而不做兜底：兜底会把元素还原成 JSONObject 等错误类型，
 * 把问题推迟到业务取值时暴露。按序号读取的重载经结果集元数据还原列标签，与按列名读取保持同一判定口径。</p>
 *
 * @author wizard-lee
 */
public class ListTypeHandler implements TypeHandler<List<?>> {

    private final JsonSerializer serializer;
    private final JdbcJsonValue jdbcJsonValue;
    private final Map<String, Type> columnListTypes;   // label -> List<E> 参数化类型
    private final Map<String, ElementConverter> converters;

    public ListTypeHandler(JsonSerializer serializer,
                           JdbcJsonValue jdbcJsonValue,
                           Map<String, Type> columnListTypes,
                           Map<String, ElementConverter> converters) {
        this.serializer = serializer;
        this.jdbcJsonValue = jdbcJsonValue;
        this.columnListTypes = Map.copyOf(columnListTypes);
        this.converters = Map.copyOf(converters);
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, List<?> parameter, JdbcType jdbcType) throws SQLException {
        // 集合列为 NOT NULL：null 统一落库为 JSON 空数组，而非 SQL NULL
        List<?> columnValue = Optional.ofNullable(parameter).orElseGet(List::of);
        // 整列写成 JSON 数组：每个元素由共享 serializer 按运行时类型分派(base→标量 / enum→code / VO→对象)
        ps.setObject(i, jdbcJsonValue.adapt(serializer.toJsonValue(columnValue)));
    }

    @Override
    public List<?> getResult(ResultSet rs, String column) throws SQLException {
        // column = 数据库列标签(JDBC 结果集列标签),即 MyBatis <result column="..."> 值 / SQL AS 别名
        Object raw = rs.getObject(column);
        if (raw == null) {
            return null;
        }
        return restore(column, raw);
    }

    @Override
    public List<?> getResult(ResultSet rs, int idx) throws SQLException {
        Object raw = rs.getObject(idx);
        if (raw == null) {
            return null;
        }
        return restore(labelOf(rs.getMetaData(), idx), raw);
    }

    @Override
    public List<?> getResult(CallableStatement cs, int idx) throws SQLException {
        Object raw = cs.getObject(idx);
        if (raw == null) {
            return null;
        }
        return restore(labelOf(cs.getMetaData(), idx), raw);
    }

    private List<?> restore(String column, Object raw) {
        Type listType = columnListTypes.get(column);
        if (listType == null) {
            throw new IllegalStateException("未登记的集合列标签：'" + column + "'。已登记标签："
                    + columnListTypes.keySet()
                    + "。请在 CollectionMapping 中声明该列(columnLabel 或 table)，"
                    + "并保证与 Mapper XML 的 column / SQL AS 别名逐字符一致。");
        }
        return applyConverters(column, serializer.fromJsonValue(raw, listType));
    }

    private String labelOf(ResultSetMetaData metaData, int idx) throws SQLException {
        if (metaData == null) {
            throw new IllegalStateException("无法获取结果集元数据，集合列(序号 " + idx
                    + ")需按列名映射：<result column=\"...\" property=\"...\"/>。");
        }
        if (idx < 1 || idx > metaData.getColumnCount()) {
            throw new IllegalStateException("集合列序号越界：" + idx
                    + "，结果集共 " + metaData.getColumnCount() + " 列。");
        }
        return metaData.getColumnLabel(idx);
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
