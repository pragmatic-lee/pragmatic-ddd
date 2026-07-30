package io.pragmatic.ddd.visual.entity;

/**
 * 字段信息 —— 承载单个字段的名称、描述、类型与归属类信息。
 *
 * @author wizard-lee
 */
public class FieldInfo {

    private final String fieldName;
    private final String description;
    private final String type;
    private final Class<?> clsType;
    public final boolean collection;


    /** 构造字段信息。 */
    public FieldInfo(String fieldName, String description, String type, Class<?> clsType, boolean collection) {
        this.fieldName = fieldName;
        this.description = description;
        this.type = type;
        this.clsType = clsType;
        this.collection = collection;
    }


    /** 返回字段名。 */
    public String getFieldName() {
        return fieldName;
    }

    /** 返回字段类型。 */
    public String getType() {
        return type;
    }

    /** 返回字段归属类类型。 */
    public Class<?> getClsType() {
        return clsType;
    }

    /** 返回字段描述。 */
    public String getDescription() {
        return description;
    }
}
