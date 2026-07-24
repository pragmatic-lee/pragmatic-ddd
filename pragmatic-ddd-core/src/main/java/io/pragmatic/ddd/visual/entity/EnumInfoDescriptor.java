package io.pragmatic.ddd.visual.entity;

import java.util.List;

public class EnumInfoDescriptor {
    private String name;
    private List<EnumValue> valueList;


    public List<EnumValue> getValueList() {
        return valueList;
    }

    public void setValueList(List<EnumValue> valueList) {
        this.valueList = valueList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
