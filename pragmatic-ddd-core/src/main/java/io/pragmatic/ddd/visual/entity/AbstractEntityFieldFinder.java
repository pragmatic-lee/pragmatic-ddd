package io.pragmatic.ddd.visual.entity;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractEntityFieldFinder implements IEntityFieldFinder {

    private final List<EntityFieldInfo> fieldGetterList = new ArrayList<>();

    protected AbstractEntityFieldFinder() {

        this.initFieldList();
    }

    protected abstract void initFieldList();

    protected <T, R> void addField(FieldGetter<T, R> fieldGetter, String description) {
        fieldGetterList.add(new EntityFieldInfo(fieldGetter, description));
    }

    protected <T, R> void addField(FieldGetter<T, R> fieldGetter, String description, Class<?> collectionType) {
        fieldGetterList.add(new EntityFieldInfo(fieldGetter, description, true, collectionType));
    }

    @Override
    public List<EntityFieldInfo> fieldGetterList() {
        return fieldGetterList;
    }
}
