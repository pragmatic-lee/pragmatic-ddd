package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.visual.application.IApplicationServiceFinder;
import io.pragmatic.ddd.visual.application.MockCommandService;
import io.pragmatic.ddd.visual.entity.AbstractEntityFieldFinder;
import io.pragmatic.ddd.visual.event.IEventFinder;
import io.pragmatic.ddd.visual.rule.IRuleFinder;
import io.pragmatic.ddd.visual.service.IDomainServiceFinder;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DomainModelVisualManagerTest {

    @Test
    public void build() {
        DomainModelVisualManager domainModelVisualManager =
                new DomainModelVisualManager(MockDomainEventManager.mockIDomainEventManager());

        domainModelVisualManager.registerDomainEntity(MockEntity.class, new AbstractEntityFieldFinder() {
            @Override
            protected void initFieldList() {
                addField(MockEntity::getAge, "年龄");
                addField(MockEntity::getMockValueObject, "模拟值对象");
                addField(MockEntity::getName, "姓名");
                addField(MockValueObject::isYes, "是");
                addField(MockValueObject::getName, "MockValue的姓名");
            }
        });
        domainModelVisualManager.registerApplicationService(MockEntity.class, new MockCommandFinder());
        domainModelVisualManager.registerDomainService(MockEntity.class, new MockIDomainServiceFinder());
        domainModelVisualManager.registerDomainEvent(MockEntity.class, new MockIEventFinder());
        domainModelVisualManager.registerDomainRule(MockEntity.class, new MockIRuleFinder());

        DomainModelVisualInfo build = domainModelVisualManager.build(MockEntity.class);
        System.out.println(JSON.toJSONString(build, JSONWriter.Feature.PrettyFormat));
    }

    static class MockIRuleFinder implements IRuleFinder {
        @Override
        public <T extends AbstractEntity<?>> RuleFinderObject findEntityRuleList(Class<T> cls) {
            ArrayList<EntityRule<?>> classes = new ArrayList<>();
            classes.add(new MockEntityRule());
            return new RuleFinderObject(classes, MockEntityBrokenRuleMessage.message);
        }
    }

    static class MockIEventFinder implements IEventFinder {
        @Override
        public <T extends AbstractEntity<?>> List<Class<?>> findersList(Class<T> cls) {
            return Stream.of(TestEvent.class).collect(Collectors.toList());
        }

        @Override
        public AbstractSubscriberKey eventSubscribeKey() {
            return MockDomainEventManager.mockAbstractSubscriberKey();
        }
    }

    static class MockCommandFinder implements IApplicationServiceFinder {
        @Override
        public <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls) {
            return Stream.of(MockCommandService.class).collect(Collectors.toList());
        }
    }

    static class MockIDomainServiceFinder implements IDomainServiceFinder {
        @Override
        public <T extends AbstractEntity<?>> List<Class<?>> findList(Class<T> cls) {
            return Stream.of(TestDomainService.class).collect(Collectors.toList());
        }
    }
}
