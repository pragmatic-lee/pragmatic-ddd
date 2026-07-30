package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.visual.entity.EntityActionVisual;
import io.pragmatic.ddd.visual.entity.EntityVisual;

import java.util.List;

@EntityVisual(description = "这个一个模拟测试类")
public class MockEntity extends AggregateRoot<Long> {


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAgeTest() {
        return ageTest;
    }

    public void setAgeTest(int ageTest) {
        this.ageTest = ageTest;
    }

    private String name;
    private Integer age;
    private int ageTest;

    private List<EntityItem> entityItems;

    private MockValueObject mockValueObject;

    public MockEntity() {
    }

    @EntityActionVisual(triggerEvents = MockEntityCreatedEvent.class,description = "")
    public MockEntity(String name, Integer age, int ageTest) {

        this.name = name;
        this.age = age;
        this.ageTest = ageTest;

        this.collectEvent(MockEntityCreatedEvent.buildEvent("mock-entity"));
    }

    public String showName() {
        return name;
    }

    @EntityActionVisual(triggerEvents = {TestEvent.class})
    public void changeBasic(String name) {
        this.name = name;
        this.collectEvent(new TestEvent());
    }

    @EntityActionVisual(triggerEvents = {TestEvent.class})
    public void changeBasic2(String name,String name2) {
        this.name = name;
        this.collectEvent(new TestEvent());
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return MockEntityBrokenRuleRegistry.INSTANCE;
    }

    @Override
    public OperationRegistry operationRegistry() {
        return MockEntityOperations.INSTANCE;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public MockValueObject getMockValueObject() {
        return mockValueObject;
    }

    public void setMockValueObject(MockValueObject mockValueObject) {
        this.mockValueObject = mockValueObject;
    }

    public List<EntityItem> getEntityItems() {
        return entityItems;
    }
}

