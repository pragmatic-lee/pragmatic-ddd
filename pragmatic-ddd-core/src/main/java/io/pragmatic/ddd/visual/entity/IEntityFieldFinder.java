package io.pragmatic.ddd.visual.entity;


import java.util.List;

public interface IEntityFieldFinder {
    List<EntityFieldInfo> fieldGetterList();


    public class EntityFieldInfo {
        public final FieldGetter<?, ?> fieldGetter;
        public final String name;
        public final boolean collection;
        public final Class<?> collectionType;

        public EntityFieldInfo(FieldGetter<?, ?> fieldGetter, String name) {
            this(fieldGetter, name, false, null);
        }

        public EntityFieldInfo(FieldGetter<?, ?> fieldGetter, String name, boolean collection, Class<?> collectionType) {
            this.fieldGetter = fieldGetter;
            this.name = name;
            this.collection = collection;
            this.collectionType = collectionType;
        }
    }

}
