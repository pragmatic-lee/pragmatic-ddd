package io.pragmatic.ddd.visual.domainservice;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.visual.DomainModelVisualManager;
import io.pragmatic.ddd.visual.MockEntity;
import io.pragmatic.ddd.visual.TestDomainService;
import io.pragmatic.ddd.visual.service.DomainServiceDescriptor;
import io.pragmatic.ddd.visual.service.DomainServiceParser;
import io.pragmatic.ddd.visual.service.IDomainServiceFinder;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DomainServiceParserTest {

    @Test
    public void domainServiceParse() {

        DomainServiceParser domainServiceParser = new DomainServiceParser();

        domainServiceParser.registerDomainService(MockEntity.class, new IDomainServiceFinder() {
            @Override
            public <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls) {
                return Stream.of(TestDomainService.class).collect(Collectors.toList());
            }
        });

        List<DomainServiceDescriptor> parse = domainServiceParser.parse(MockEntity.class);

        System.out.println(JSON.toJSONString(parse));


        DomainModelVisualManager domainModelVisualManager = new DomainModelVisualManager(null);
        domainModelVisualManager.registerDomainEntity(MockEntity.class, null);


    }
}




