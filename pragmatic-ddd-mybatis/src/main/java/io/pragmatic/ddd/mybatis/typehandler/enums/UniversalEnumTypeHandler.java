package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 通用枚举类型处理器：单个泛型类覆盖所有枚举，按 {@link EnumRule} 序列化/反序列化。
 *
 * @author wizard-lee
 */
public class UniversalEnumTypeHandler<E extends Enum<E>> implements TypeHandler<E> {
    private final Class<E> enumType;
    private final EnumRule rule;
    private final EnumValueResolver resolver;

    // 首参放宽到 Class<? extends Enum<?>>，内部一次性 unchecked 转 Class<E>
    @SuppressWarnings("unchecked")
    public UniversalEnumTypeHandler(Class<? extends Enum<?>> enumType, EnumRule rule, EnumValueResolver resolver) {
        this.enumType = (Class<E>) enumType;
        this.rule = rule;
        this.resolver = resolver;
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, E p, JdbcType jdbcType) throws SQLException {
        if (p == null) {
            ps.setNull(i, JdbcType.INTEGER.TYPE_CODE);
            return;
        }
        Object col = switch (rule) {                 // 序列化:枚举 → 列
            case NAME    -> p.name();
            case ORDINAL -> p.ordinal();
            case CODE    -> ((IEnumValue<?, ?>) p).getValue();
            case LABEL   -> ((IEnumValue<?, ?>) p).getName();
        };
        ps.setObject(i, col);
    }

    @Override
    public E getResult(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);           // 反序列化:列 → 枚举
        return raw == null ? null : resolver.resolve(enumType, raw, rule);
    }

    @Override
    public E getResult(ResultSet rs, int idx) throws SQLException {
        Object raw = rs.getObject(idx);
        return raw == null ? null : resolver.resolve(enumType, raw, rule);
    }

    @Override
    public E getResult(CallableStatement cs, int idx) throws SQLException {
        Object raw = cs.getObject(idx);
        return raw == null ? null : resolver.resolve(enumType, raw, rule);
    }
}
