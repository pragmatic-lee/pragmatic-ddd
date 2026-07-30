package io.pragmatic.ddd.visual.application;

import io.pragmatic.ddd.base.AbstractEntity;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用服务解析器 —— 借助查找器扫描实体类，提取标注的可视化方法并构建描述符。
 *
 * @author wizard-lee
 */
public class ApplicationServiceParser {


    private final Map<Class<?>, IApplicationServiceFinder> applicationServiceFinderMap = new HashMap<>();

    /** 注册某实体类的应用服务查找器。 */
    public <T extends AbstractEntity<?>> void registerApplicationService(Class<T> entityClass,
                                                                         IApplicationServiceFinder finder) {
        applicationServiceFinderMap.put(entityClass, finder);
    }

    /** 解析实体类的全部应用服务方法，返回描述符列表。 */
    public <T extends AbstractEntity<?>> List<ApplicationDescriptor> parser(Class<T> cls) {

        List<Class<?>> list = Optional.ofNullable(this.applicationServiceFinderMap.get(cls))
                .map(s -> s.findList(cls))
                .orElse(Collections.emptyList());


        return list.stream()
                .map(s -> Arrays.stream(s.getMethods())
                        .filter(m -> Modifier.isPublic(m.getModifiers()))
                        .map(m -> {

                            CommandServiceVisual commandServiceDescriptor =
                                    m.getAnnotation(CommandServiceVisual.class);

                            ReadServiceVisual readServiceDescriptor =
                                    m.getAnnotation(ReadServiceVisual.class);

                            if (commandServiceDescriptor == null && readServiceDescriptor == null) {
                                return null;
                            }

                            String name = Optional.ofNullable(commandServiceDescriptor)
                                    .map(CommandServiceVisual::name)
                                    .orElse(Optional.ofNullable(readServiceDescriptor)
                                            .map(ReadServiceVisual::name)
                                            .orElse("")
                                    );
                            String description = Optional.ofNullable(commandServiceDescriptor)
                                    .map(CommandServiceVisual::description)
                                    .orElse(Optional.ofNullable(readServiceDescriptor)
                                            .map(ReadServiceVisual::description)
                                            .orElse("")
                                    );
                            String type = Optional.ofNullable(commandServiceDescriptor).map(t -> {
                                return "Command";
                            }).orElse("Query");


                            return new ApplicationDescriptor(
                                    name,
                                    description,
                                    m.getDeclaringClass().getSimpleName(),
                                    m.getName(),
                                    type
                            );
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))

                .flatMap(List::stream)
                .collect(Collectors.toList());

    }
}
