package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.repository.*;
import org.junit.Test;

import java.util.Comparator;
import java.util.List;

public class EntityBaseTest {


    @Test
    public void setAndReturnOldTest() {
        TestData testData = new TestData();
        testData.setData2(1);
        Integer i = testData.updateData2Info(1000);

        assert i == 1;
        assert 100 == testData.getData2();


    }

    @Test
    public void compareAndSetTest() {
        TestData testData = new TestData();

        testData.setData(false);

        CompareAndSetInfo<Boolean> booleanCompareAndSetInfo = testData.updateData(true);

        System.out.println(booleanCompareAndSetInfo);

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
    public void remove(TestData entityBase) {


    }
}

class TestDataReadRepository implements
        IIDByReadDAORepository<TestDataDAO, Long>,
        IPageAndScrollByReadDAORepository<TestDataDAO, TestDataPageByQueryDAO, TestDataScrollByQueryDAO>,
        IListByReadDAORepository<TestDataDAO, TestDataListByQueryDAO> {

    @Override
    public TestDataDAO queryById(Long orderId, String returnClassName) {
        return null;
    }

    @Override
    public List<TestDataDAO> queryByIdList(List<Long> orderIdList, String returnClassName) {
        return IIDByReadDAORepository.super.queryByIdList(orderIdList, returnClassName);
    }

    @Override
    public TestDataDAO queryOneBy(TestDataListByQueryDAO testDataListByQueryDAO, String returnClassName) {
        return null;
    }

    @Override
    public PageInfoDAO<TestDataDAO> queryPageBy(TestDataPageByQueryDAO orderPageQueryDTO,
                                                PageNumberDAO pageNumber,
                                                String returnClassName) {
        return null;
    }

    @Override
    public List<TestDataDAO> queryScrollBy(TestDataPageByQueryDAO orderPageQueryDTO,
                                           TestDataScrollByQueryDAO scrollQueryDTO,
                                           String returnClassName) {
        return IPageAndScrollByReadDAORepository.super.queryScrollBy(orderPageQueryDTO,
                scrollQueryDTO, returnClassName);
    }
}

class TestDataDAO {

}

class TestDataListByQueryDAO {

}

class TestDataPageByQueryDAO {

}

class TestDataScrollByQueryDAO {

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


    public Integer updateData2Info(Integer integer) {

        return this.setAndReturnOld(this::setData2, this::getData2, integer);
    }

    public CompareAndSetInfo<Boolean> updateData(Boolean integer) {
        return this.compareAndSet(integer, this.getData(), this::setData);
    }


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
