package io.pragmatic.ddd.visual.entity;

public class FieldInfo {

    private final String fieldName;
    private final String description;
    private final String type;
    private final Class<?> clsType;
    public final boolean collection;


    public FieldInfo(String fieldName, String description, String type, Class<?> clsType, boolean collection) {
        this.fieldName = fieldName;
        this.description = description;
        this.type = type;
        this.clsType = clsType;
        this.collection = collection;
    }


    public String getFieldName() {
        return fieldName;
    }

    public String getType() {
        return type;
    }

    public Class<?> getClsType() {
        return clsType;
    }

    public String getDescription() {
        return description;
    }
}
