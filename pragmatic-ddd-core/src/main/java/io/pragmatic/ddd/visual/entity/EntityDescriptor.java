package io.pragmatic.ddd.visual.entity;

import java.util.List;

/**
 * 实体描述符 —— 承载实体的名称、描述、字段列表与行为列表信息。
 *
 * @author wizard-lee
 */
public class EntityDescriptor {
    private final String description;

    private final Boolean root;
    private final String clsName;

    private final List<FieldInfo> fieldInfoList;

    private final List<EntityActionDescriptor> entityActionDescriptorList;

    /** 构造实体描述符。 */
    public EntityDescriptor(String clsName, String description, List<FieldInfo> fieldInfoList,
                            List<EntityActionDescriptor> entityActionDescriptorList, Boolean root) {

        this.clsName = clsName;
        this.description = description;
        this.fieldInfoList = fieldInfoList;
        this.entityActionDescriptorList = entityActionDescriptorList;
        this.root = root;
    }

    /** 返回实体类名。 */
    public String getClsName() {
        return clsName;
    }

    /** 返回实体描述。 */
    public String getDescription() {
        return description;
    }

    /** 是否为聚合根。 */
    public Boolean getRoot() {
        return root;
    }

    /** 返回字段信息列表。 */
    public List<FieldInfo> getFieldInfoList() {
        return fieldInfoList;
    }

    /** 返回实体行为描述符列表。 */
    public List<EntityActionDescriptor> getEntityActionDescriptorList() {
        return entityActionDescriptorList;
    }
}
