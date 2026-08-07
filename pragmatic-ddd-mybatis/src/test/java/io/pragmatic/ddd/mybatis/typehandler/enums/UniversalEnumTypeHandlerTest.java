package io.pragmatic.ddd.mybatis.typehandler.enums;

import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.mybatis.support.JdbcMocks;
import io.pragmatic.ddd.mybatis.support.JdbcMocks.PsRecorder;
import org.apache.ibatis.type.JdbcType;

import java.sql.ResultSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UniversalEnumTypeHandler 纯单测：setParameter 写出库存标量，getResult 还原枚举。
 *
 * @author wizard-lee
 */
@DisplayName("UniversalEnumTypeHandler JDBC 绑定")
class UniversalEnumTypeHandlerTest {

    enum StatusEnum implements IEnumValue<Integer, StatusEnum> {
        NORMAL(1, "正常"),
        DISABLED(2, "禁用"),
        PENDING(3, "待处理");

        private final Integer code;
        private final String label;

        StatusEnum(Integer code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return code;
        }

        @Override
        public String getName() {
            return label;
        }
    }

    private final EnumValueResolver resolver = new EnumValueResolver();
    private final UniversalEnumTypeHandler<StatusEnum> handler =
            new UniversalEnumTypeHandler<>(StatusEnum.class, EnumRule.CODE, resolver);

    @Test
    @DisplayName("setParameter 将枚举写出为其 code")
    void setParameterWritesCode() throws Exception {
        PsRecorder recorder = new PsRecorder();
        handler.setParameter(JdbcMocks.fakePreparedStatement(recorder), 1, StatusEnum.PENDING, JdbcType.INTEGER);

        assertThat(recorder.objectParam(1)).isEqualTo(3);
    }

    @Test
    @DisplayName("setParameter 传 null 时写为 NULL")
    void setParameterWritesNull() throws Exception {
        PsRecorder recorder = new PsRecorder();
        handler.setParameter(JdbcMocks.fakePreparedStatement(recorder), 2, null, JdbcType.INTEGER);

        assertThat(recorder.nullType(2)).isEqualTo(JdbcType.INTEGER.TYPE_CODE);
    }

    @Test
    @DisplayName("getResult 按列名从库存标量还原枚举")
    void getResultByColumn() throws Exception {
        ResultSet rs = JdbcMocks.fakeResultSetWithColumn("status", 2, false);

        StatusEnum result = handler.getResult(rs, "status");

        assertThat(result).isEqualTo(StatusEnum.DISABLED);
    }

    @Test
    @DisplayName("getResult 数据库 NULL 还原为 null")
    void getResultNull() throws Exception {
        ResultSet rs = JdbcMocks.fakeResultSetWithColumn("status", null, true);

        assertThat(handler.getResult(rs, "status")).isNull();
    }

    @Test
    @DisplayName("getResult 下标分支也能还原枚举")
    void getResultByIndex() throws Exception {
        ResultSet rs = JdbcMocks.fakeResultSetWithIndex(1, 1, false);

        StatusEnum result = handler.getResult(rs, 1);

        assertThat(result).isEqualTo(StatusEnum.NORMAL);
    }
}
