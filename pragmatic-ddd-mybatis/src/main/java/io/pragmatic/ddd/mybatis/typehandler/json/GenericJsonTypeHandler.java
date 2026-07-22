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
 * 通用 JSON 值对象类型处理器——把 VO（含枚举）整体委托给 {@link JsonSerializer} 读写原生 JSON 列。
 *
 * <p>列类型统一为数据库原生 JSON（PG {@code jsonb}/{@code json}，MySQL {@code JSON}），
 * 因此 {@code setParameter} 用 {@code PreparedStatement.setObject(i, jsonValue)} 写入结构化 JSON 值，
 * 不再 {@code setString} + {@code VARCHAR}；{@code getResult} 用 {@code getObject} 读回任意 JSON 形态后
 * 交给 {@code serializer.fromJsonValue} 还原。与枚举通道 {@code UniversalEnumTypeHandler} 的
 * setObject / getObject 风格保持一致。
 *
 * <p>VO 内枚举的序列化/反序列化全部转发给 {@code serializer}（{@code Fastjson2JsonSerializer} 的
 * 策略感知逻辑兜底）；写入/读回均走结构化 JSON 值，与单列枚举通道形态一致。
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
