package io.pragmatic.ddd.mybatis.typehandler.json;

import io.pragmatic.ddd.mybatis.spi.JsonSerializer;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 通用 JSON 值对象类型处理器：把 VO（含枚举）整体委托 {@link JsonSerializer} 读写原生 JSON 列。
 *
 * @author wizard-lee
 */
public class GenericJsonTypeHandler<T> implements TypeHandler<T> {

    private final Class<T> voType;
    private final JsonSerializer serializer;
    private final JdbcJsonValue jdbcJsonValue;

    public GenericJsonTypeHandler(Class<T> voType, JsonSerializer serializer, JdbcJsonValue jdbcJsonValue) {
        this.voType = voType;
        this.serializer = serializer;
        this.jdbcJsonValue = jdbcJsonValue;
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setNull(i, Types.OTHER);           // JSON 列统一用 Types.OTHER（多数驱动识别为 json/jsonb）
            return;
        }
        // 结构化 JSON 值写入原生 JSON 列；驱动差异由 jdbcJsonValue 适配（PG → PGobject）
        ps.setObject(i, jdbcJsonValue.adapt(serializer.toJsonValue(parameter)));
    }

    @Override
    public T getResult(ResultSet rs, String column) throws SQLException {
        return serializer.fromJsonValue(rs.getObject(column), voType);   // 读回: PGobject / JSONObject / String
    }

    @Override
    public T getResult(ResultSet rs, int idx) throws SQLException {
        return serializer.fromJsonValue(rs.getObject(idx), voType);
    }

    @Override
    public T getResult(CallableStatement cs, int idx) throws SQLException {
        return serializer.fromJsonValue(cs.getObject(idx), voType);
    }
}
