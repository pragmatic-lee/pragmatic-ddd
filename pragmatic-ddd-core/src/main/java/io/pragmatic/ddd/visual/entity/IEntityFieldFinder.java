package io.pragmatic.ddd.visual.entity;


import java.util.List;

/**
 * 实体字段查找器契约 —— 导出实体字段读取器信息列表。
 *
 * @author wizard-lee
 */
public interface IEntityFieldFinder {
    /** 返回实体字段读取器信息列表。 */
    List<EntityFieldInfo> fieldGetterList();


    /**
     * 字段读取器信息 —— 承载读取器、名称、是否集合及集合元素类型。
     */
    public class EntityFieldInfo {
        public final FieldGetter<?, ?> fieldGetter;
        public final String name;
        public final boolean collection;
        public final Class<?> collectionType;

        /** 构造普通字段读取器信息。 */
        public EntityFieldInfo(FieldGetter<?, ?> fieldGetter, String name) {
            this(fieldGetter, name, false, null);
        }

        /** 构造字段读取器信息（可指定集合与元素类型）。 */
        public EntityFieldInfo(FieldGetter<?, ?> fieldGetter, String name, boolean collection, Class<?> collectionType) {
            this.fieldGetter = fieldGetter;
            this.name = name;
            this.collection = collection;
            this.collectionType = collectionType;
        }
    }

}
