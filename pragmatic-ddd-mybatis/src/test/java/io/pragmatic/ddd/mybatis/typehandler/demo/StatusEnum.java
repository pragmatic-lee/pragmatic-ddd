package io.pragmatic.ddd.mybatis.typehandler.demo;

import io.pragmatic.ddd.base.IEnumValue;

/**
 * 演示枚举：CODE 策略持久化维度为 value（INT），用于单列枚举列与 VO 内嵌枚举字段。
 *
 * @author wizard-lee
 */
public enum StatusEnum implements IEnumValue<Integer, StatusEnum> {
    ACTIVE(1, "启用"),
    INACTIVE(2, "停用"),
    ARCHIVED(3, "归档");

    private final int code;
    private final String label;

    StatusEnum(int code, String label) {
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
