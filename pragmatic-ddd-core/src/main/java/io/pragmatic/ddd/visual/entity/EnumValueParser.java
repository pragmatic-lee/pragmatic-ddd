package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.visual.VisualException;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public class EnumValueParser {
    private final Map<Class<?>, IEnumValueFinder> enumValueFinderMap = new HashMap<>();

    public <T extends AbstractEntity<?>> void registerEnum(Class<T> entityClass, IEnumValueFinder finder) {
        this.enumValueFinderMap.put(entityClass, finder);
    }

    public <T extends AbstractEntity<?>> List<EnumInfoDescriptor> parse(Class<T> cls) {

        IEnumValueFinder iEnumValueFinder = this.enumValueFinderMap.get(cls);
        if (iEnumValueFinder == null) {
            return Collections.emptyList();
        }

        List<Class<?>> enums = iEnumValueFinder.findEnums();

        return enums.stream().map(e -> {
            EnumInfoDescriptor descriptor = new EnumInfoDescriptor();
            descriptor.setName(e.getSimpleName());
            descriptor.setValueList(parseEnumValues(e));

            return descriptor;
        }).collect(Collectors.toList());
    }

    private List<EnumValue> parseEnumValues(Class<?> cls) {
        try {
            Object[] values = (Object[]) (cls.getMethod("values").invoke(null));
            return Arrays.stream(values).map(IEnumValue.class::cast).map(v -> {

                EnumValue enumValue = new EnumValue();
                enumValue.setValue(v.getValue().toString());
                enumValue.setDescription(v.getName());
                enumValue.setName(v.toString());
                return enumValue;
            }).collect(Collectors.toList());

        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new VisualException(e);
        }
    }
}
