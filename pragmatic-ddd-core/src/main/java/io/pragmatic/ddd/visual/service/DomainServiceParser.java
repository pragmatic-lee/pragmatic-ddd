package io.pragmatic.ddd.visual.service;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.base.IDomainService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 领域服务解析器 —— 借助查找器扫描实体类，提取标注的领域服务并构建描述符。
 *
 * @author wizard-lee
 */
public class DomainServiceParser {

    private final Map<Class<?>, IDomainServiceFinder> domainServiceMap = new HashMap<>();


    /** 注册某实体类的领域服务查找器。 */
    public <T extends AbstractEntity<?>> void registerDomainService(Class<T> entityClass, IDomainServiceFinder finder) {
        this.domainServiceMap.put(entityClass, finder);
    }

    /** 解析实体类的全部领域服务，返回描述符列表。 */
    public <T extends AbstractEntity<?>> List<DomainServiceDescriptor> parse(Class<T> cls) {

        List<Class<?>> domainServiceClsList = Optional.ofNullable(this.domainServiceMap.get(cls))
                .map(f->f.findList(cls)).orElse(Collections.emptyList());

        return domainServiceClsList.stream().map(s -> {

            if (Arrays.stream(s.getInterfaces()).anyMatch(inter -> inter == IDomainService.class)) {

                String description = Optional.ofNullable(s.getAnnotation(DomainServiceVisual.class))
                        .map(DomainServiceVisual::description)
                        .orElse("");

                return new DomainServiceDescriptor(s.getSimpleName(), description);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());

    }
}
