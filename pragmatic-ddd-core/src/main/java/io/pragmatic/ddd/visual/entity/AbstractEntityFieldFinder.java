package io.pragmatic.ddd.visual.entity;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体字段查找器基类 —— 子类通过 {@code addField} 登记字段读取器，统一导出字段信息。
 *
 * @author wizard-lee
 */
public abstract class AbstractEntityFieldFinder implements IEntityFieldFinder {

    private final List<EntityFieldInfo> fieldGetterList = new ArrayList<>();

    protected AbstractEntityFieldFinder() {

        this.initFieldList();
    }

    /** 子类实现：登记全部字段读取器。 */
    protected abstract void initFieldList();

    /** 登记一个普通字段读取器及其描述。 */
    protected <T, R> void addField(FieldGetter<T, R> fieldGetter, String description) {
        fieldGetterList.add(new EntityFieldInfo(fieldGetter, description));
    }

    /** 登记一个集合类型字段读取器及其描述。 */
    protected <T, R> void addField(FieldGetter<T, R> fieldGetter, String description, Class<?> collectionType) {
        fieldGetterList.add(new EntityFieldInfo(fieldGetter, description, true, collectionType));
    }

    /** 导出已登记的字段读取器列表。 */
    @Override
    public List<EntityFieldInfo> fieldGetterList() {
        return fieldGetterList;
    }
}
