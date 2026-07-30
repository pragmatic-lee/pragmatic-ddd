package io.pragmatic.ddd.visual.entity;

import java.util.List;

/**
 * 枚举信息描述符 —— 承载单个枚举类型的名称与其值列表。
 *
 * @author wizard-lee
 */
public class EnumInfoDescriptor {
    private String name;
    private List<EnumValue> valueList;


    /** 返回枚举值列表。 */
    public List<EnumValue> getValueList() {
        return valueList;
    }

    /** 设置枚举值列表。 */
    public void setValueList(List<EnumValue> valueList) {
        this.valueList = valueList;
    }

    /** 返回枚举类型名称。 */
    public String getName() {
        return name;
    }

    /** 设置枚举类型名称。 */
    public void setName(String name) {
        this.name = name;
    }
}
