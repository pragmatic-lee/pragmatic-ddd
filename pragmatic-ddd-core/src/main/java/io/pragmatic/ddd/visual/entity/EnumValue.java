package io.pragmatic.ddd.visual.entity;

/**
 * 枚举值描述符 —— 承载单个枚举值的取值、名称与描述。
 *
 * @author wizard-lee
 */
public class EnumValue {
    private String value;
    private String name;
    private String description;

    /** 返回枚举取值。 */
    public String getValue() {
        return value;
    }

    /** 设置枚举取值。 */
    public void setValue(String value) {
        this.value = value;
    }

    /** 返回枚举名称。 */
    public String getName() {
        return name;
    }

    /** 设置枚举名称。 */
    public void setName(String name) {
        this.name = name;
    }

    /** 返回枚举描述。 */
    public String getDescription() {
        return description;
    }

    /** 设置枚举描述。 */
    public void setDescription(String description) {
        this.description = description;
    }
}
