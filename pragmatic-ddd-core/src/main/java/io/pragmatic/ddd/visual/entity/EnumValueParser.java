package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.base.IEnumValue;
import io.pragmatic.ddd.visual.VisualException;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 枚举值解析器 —— 借助查找器扫描实体类，提取枚举类型及其取值并构建描述符。
 *
 * @author wizard-lee
 */
public class EnumValueParser {
    private final Map<Class<?>, IEnumValueFinder> enumValueFinderMap = new HashMap<>();

    /** 注册某实体类的枚举值查找器。 */
    public <T extends AbstractEntity<?>> void registerEnum(Class<T> entityClass, IEnumValueFinder finder) {
        this.enumValueFinderMap.put(entityClass, finder);
    }

    /** 解析实体类的全部枚举类型，返回枚举信息描述符列表。 */
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
