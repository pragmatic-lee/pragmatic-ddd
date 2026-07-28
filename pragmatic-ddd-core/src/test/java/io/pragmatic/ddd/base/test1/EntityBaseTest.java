package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.repository.*;
import org.junit.Test;

import java.util.Comparator;

public class EntityBaseTest {


    @Test
    public void setAndReturnOldTest() {
        TestData testData = new TestData();
        testData.setData2(1);


        assert 100 == testData.getData2();


    }


}


class ValueObject {

    private Integer v1;
    private String name;

    public Integer getV1() {
        return v1;
    }

    public void setV1(Integer v1) {
        this.v1 = v1;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

class TestDataRepository implements IRepository<Long, TestData> {

    @Override
    public void insert(TestData entityBase) {

    }

    @Override
    public void update(TestData entityBase) {

    }

    @Override
    public TestData findById(Long aLong) {
        return null;
    }

    @Override
    public void removeById(Long aLong) {
        // 测试桩：无实际操作
    }
}


class TestData extends AggregateRoot<Long> {
    private Boolean data;
    private Integer data2;

    private ValueObject valueObject;

    public TestData() {
        this.valueObject = new ValueObject();
        this.valueObject.setName("new name");
        this.valueObject.setV1(1000);
    }

    public static final Comparator<ValueObject> valueObjectComparator = Comparator
            .comparing(ValueObject::getName)
            .thenComparing(ValueObject::getV1);






    public Boolean getData() {
        return data;
    }

    public void setData(Boolean data) {
        this.data = data;
    }

    public Integer getData2() {
        return data2;
    }

    public void setData2(Integer data2) {
        this.data2 = data2;
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return null;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return null;
    }

    public ValueObject getValueObject() {
        return valueObject;
    }

    public void setValueObject(ValueObject valueObject) {
        this.valueObject = valueObject;
    }
}
