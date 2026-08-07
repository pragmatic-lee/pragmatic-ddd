package io.pragmatic.ddd.mybatis.typehandler.demo;

import io.pragmatic.ddd.base.IEnumValue;

/**
 * 演示枚举：作为 JSON List 的元素类型，CODE 策略持久化维度为 value（INT）。
 *
 * @author wizard-lee
 */
public enum ColorEnum implements IEnumValue<Integer, ColorEnum> {
    RED(10, "红"),
    GREEN(20, "绿"),
    BLUE(30, "蓝");

    private final int code;
    private final String label;

    ColorEnum(int code, String label) {
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
