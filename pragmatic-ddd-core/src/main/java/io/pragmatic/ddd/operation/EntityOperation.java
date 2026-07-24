package io.pragmatic.ddd.operation;

import java.util.Objects;

/**
 * 实体业务操作描述符（不可变值对象，非枚举）。
 * <p>对应设计文档 3.2 推荐方案：替代原 {@code action.Action}，
 * 作为 {@link OperationRegistry} 反射自动注册的基本单元。
 * 与值对象枚举在结构上区分（见设计文档 3.2.0），避免与 VO 枚举混放。</p>
 */
public final class EntityOperation implements IEntityOperation {

    private final String code;
    private final String description;

    private EntityOperation(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static EntityOperation of(String code, String description) {
        return new EntityOperation(code, description);
    }

    public static EntityOperation of(String code) {
        return new EntityOperation(code, "");
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityOperation that)) {
            return false;
        }
        return Objects.equals(code, that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
