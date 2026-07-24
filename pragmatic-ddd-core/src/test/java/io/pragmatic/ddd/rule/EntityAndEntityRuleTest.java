package io.pragmatic.ddd.rule;

import io.pragmatic.ddd.base.*;
import io.pragmatic.ddd.operation.OperationRegistry;
import io.pragmatic.ddd.rules.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.Objects;

import static io.pragmatic.ddd.rule.EntityAndEntityRuleTest.DataBrokenRuleMessage.*;

/**
 * @author lixiaojing
 * @date 2021/2/22 10:18 上午
 */
public class EntityAndEntityRuleTest {

    @Test
    public void basicRuleTest() {

        Data data = new Data();

        EntityRule<Data> dataEntityRule = new DataEntityRule();
        Assert.assertFalse(dataEntityRule.satisfiesRule(data));

        BrokenRuleException brokenRuleException = data.exceptionCause();

        Assert.assertEquals(brokenRuleException.getEntityInfo(), data.getEntityId().toString());

    }

    @Test
    public void entityTest() {
        Data data = new Data();
        DataEntityRule rule = new DataEntityRule();

        Assert.assertFalse(data.satisfiesRule(rule));
        Assert.assertEquals(2, data.getBrokenRules().size());

        data.clearBrokenRules();
        data.setStatus(1);
        data.satisfiesRule(new DataEntityRule());
        Assert.assertEquals(2, data.getBrokenRules().size());

        data.clearBrokenRules();
        data.setPrice(10.9);
        data.setName("张三");
        data.setStatus(0);

        Assert.assertFalse(data.satisfiesRule(new DataEntityRule()));
        Assert.assertEquals(1, data.getBrokenRules().size());
        Assert.assertEquals("price_equal_error", data.getBrokenRules().get(0).getDescription());


    }

    @Test
    public void oneRuleTest() {
        Data data = new Data();

        DataEntityRule dataEntityRule = new DataEntityRule();
        IRule<Data> clsRule = dataEntityRule.findRuleByMessageKey(PRICE_ZERO_ERROR);

        boolean satisfy = clsRule.satisfiesRule(data);
        Assert.assertFalse(satisfy);

        IRule<Data> propertyRule = dataEntityRule.findRuleByMessageKey(DataBrokenRuleMessage.NAME_EMPTY_ERROR);

        boolean satisfy1 = propertyRule.satisfiesRule(data);
        Assert.assertFalse(satisfy1);


    }

    @Test
    public void replaceRuleTest() {
        DataEntityRule dataEntityRule = new DataEntityRule();
        Data data = new Data();
        data.setName("12");
        data.setPrice(2.0);

        boolean satisfy = dataEntityRule.satisfiesRule(data);
        Assert.assertTrue(satisfy);

        dataEntityRule.replaceRule(s -> s.getPrice().equals(3.0),
                price_equal_error, price_equal_error);

        data.clearBrokenRules();

        boolean satisfy1 = dataEntityRule.satisfiesRule(data);
        Assert.assertFalse(satisfy1);

        data.clearBrokenRules();

        dataEntityRule.removeRule(price_equal_error);

        dataEntityRule.replaceRule(s -> s.getPrice() < 0, PRICE_ZERO_ERROR, PRICE_ZERO_1_ERROR);

        boolean satisfy2 = dataEntityRule.satisfiesRule(data);
        Assert.assertFalse(satisfy2);
    }

    @Test
    public void removeTest() {
        DataEntityRule dataEntityRule = new DataEntityRule();

        Data data = new Data();
        data.setPrice(2.0);

        //移除之前规则
        boolean satisfy = dataEntityRule.satisfiesRule(data);
        Assert.assertFalse(satisfy);

        BrokenRule brokenRule = data.getBrokenRules().get(0);
        Assert.assertEquals(brokenRule.getName(), DataBrokenRuleMessage.NAME_EMPTY_ERROR);

        data.clearBrokenRules();

        //移除之后规则
        dataEntityRule.removeRule(DataBrokenRuleMessage.NAME_EMPTY_ERROR);
        boolean satisfy1 = dataEntityRule.satisfiesRule(data);
        Assert.assertTrue(satisfy1);

        data.setPrice(0.0);

        data.clearBrokenRules();

        boolean satisfy2 = dataEntityRule.satisfiesRule(data);
        Assert.assertFalse(satisfy2);

        data.clearBrokenRules();

        dataEntityRule.removeRule(PRICE_ZERO_ERROR);
        dataEntityRule.removeRule(price_equal_error);
        boolean satisfy3 = dataEntityRule.satisfiesRule(data);
        Assert.assertTrue(satisfy3);
    }

    @Test
    public void resetTest() {

        DataEntityRule dataEntityRule = new DataEntityRule();

        Data data = new Data();
        data.setPrice(2.0);

        //移除之前规则
        boolean satisfy = dataEntityRule.satisfiesRule(data);
        Assert.assertFalse(satisfy);

        data.clearBrokenRules();

        dataEntityRule.removeRule(NAME_EMPTY_ERROR);

        boolean satisfy2 = dataEntityRule.satisfiesRule(data);
        Assert.assertTrue(satisfy2);

        dataEntityRule.reset();

        boolean satisfy3 = dataEntityRule.satisfiesRule(data);
        Assert.assertFalse(satisfy3);


    }

    @Test
    public void aggeTest() {

        Data data = new Data();

        boolean satisfy = new DataEntityRule().satisfiesRule(data);

        assert !satisfy;

        assert data.aggregateExceptionCause().getExceptions().size() > 0;
    }

    @Test
    public void failFastTest() {
        Data data = new Data();
        data.setStatus(1);
        boolean satisfy = new DataEntityFailFastRule(false).satisfiesRule(data);
        assert !satisfy;
        assert data.aggregateExceptionCause().getExceptions().size() > 0;


    }


    @Test
    public void failFastTest2() {

        Data data = new Data();
        data.setStatus(1);

        boolean satisfy = new DataEntityFailFastRule2(false).satisfiesRule(data);
        assert !satisfy;
        assert data.aggregateExceptionCause().getExceptions().size() > 0;
    }

    static class DataEntityFailFastRule2 extends EntityRule<Data> {

        public DataEntityFailFastRule2(boolean failFast) {
            super(failFast);
            this.init();
        }

        @Override
        protected Data supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(s -> !Objects.equals(s.getName(), ""), NAME_EMPTY_ERROR);
            this.addRule(s -> s.getPrice() > 0, PRICE_ZERO_ERROR);
            //在特定条件下，参与验证
            this.addRule(Data::getBoolInfo, DataBrokenRuleMessage.BOOLEAN_INFO_ERROR,
                    model -> model.getStatus() == 1 ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);
            //带参数的方式验证
            this.addParamRule(model -> RuleCheckResult.fail(new Object[]{model.getName()}),
                    DataBrokenRuleMessage.NAME_USED_ERROR,
                    model -> !model.getName().isEmpty() ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);
        }
    }


    static class DataEntityFailFastRule extends EntityRule<Data> {

        public DataEntityFailFastRule(boolean failFast) {
            super(failFast);
            this.init();
        }

        @Override
        protected Data supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(model -> model.getPrice() > 0, PRICE_ZERO_ERROR);
            //在特定条件下，参与验证
            this.addRule(Data::getBoolInfo, DataBrokenRuleMessage.BOOLEAN_INFO_ERROR,
                    model -> model.getStatus() == 1 ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);
            //带参数的方式验证
            this.addParamRule(new TestRule(), NAME_USED_ERROR);
        }
    }

    static class TestRule implements IParamRuleBuilder<Data> {

        @Override
        public IParamRule<Data> rule() {
            return t -> RuleCheckResult.fail(new Object[]{t.getName()});
        }

        @Override
        public IActiveRuleCondition<Data> ruleCondition() {
            return t -> !t.getName().isEmpty() ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE;
        }
    }


    static class DataEntityRule extends EntityRule<Data> {
        public DataEntityRule() {
            this.init();
        }

        @Override
        protected Data supplyOldEntity() {
            return null;
        }

        @Override
        public void init() {
            this.addRule(model -> !model.getName().isEmpty(),
                    DataBrokenRuleMessage.NAME_EMPTY_ERROR);
            this.addRule(model -> model.getPrice() > 0, PRICE_ZERO_ERROR);
            this.addRule(model -> Objects.equals(model.getPrice(), 2.0),
                    price_equal_error);
            //在特定条件下，参与验证
            this.addRule(Data::getBoolInfo, DataBrokenRuleMessage.BOOLEAN_INFO_ERROR,
                    model -> model.getStatus() == 1 ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);
            //带参数的方式验证
            this.addParamRule(model -> RuleCheckResult.fail(new Object[]{model.getName()}),
                    DataBrokenRuleMessage.NAME_USED_ERROR,
                    model -> !model.getName().isEmpty() ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE);
        }
    }

    static class DataBrokenRuleMessage extends BrokenRuleMessage {

        public static final String NAME_EMPTY_ERROR = "NAME_EMPTY_ERROR";
        public static final String NAME_USED_ERROR = "NAME_USED_ERROR";
        public static final String PRICE_ZERO_ERROR = "PRICE_ZERO_ERROR";
        public static final String PRICE_ZERO_1_ERROR = "PRICE_ZERO_1_ERROR";
        public static final String price_equal_error = "price_equal_error";
        public static final String BOOLEAN_INFO_ERROR = "BOOLEAN_INFO_ERROR";

        @Override
        protected void populateMessage() {

            this.getMessages().put(NAME_EMPTY_ERROR, "名字不能为空");

            this.getMessages().put(NAME_USED_ERROR, "%s这个名字已经使用");
            this.getMessages().put(PRICE_ZERO_ERROR, "价格不能是0");
            this.getMessages().put(PRICE_ZERO_1_ERROR, "价格必须小于0");
            this.getMessages().put(BOOLEAN_INFO_ERROR, "must be TRUE");
            this.getMessages().put(price_equal_error, "price_equal_error");

        }
    }


    static class DataExtension {
        private long extId;

        public DataExtension(long extId) {
            this.setExtId(extId);
        }

        public DataExtension() {
        }

        public long getExtId() {
            return extId;
        }

        public void setExtId(long extId) {
            this.extId = extId;
        }
    }

    static class Data extends AbstractEntity<Long> {

        private String name = "";
        private Double price;
        private Boolean boolInfo;
        private Date greaterData;
        private int status;

        private DataExtension dataExtension;

        public Data() {
            this.setEntityId(10000L);
        }

        public Boolean getBoolInfo() {
            return boolInfo != null && boolInfo;
        }

        public void setBoolInfo(Boolean boolInfo) {
            this.boolInfo = boolInfo;
        }

        @Override
        protected BrokenRuleMessage getBrokenRuleMessages() {
            return new DataBrokenRuleMessage();
        }

        @Override
        protected OperationRegistry entityOperations() {
            return null;
        }

        public Date getGreaterData() {
            return greaterData;
        }

        public void setGreaterData(Date greaterData) {
            this.greaterData = greaterData;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Double getPrice() {
            return price == null ? 0.0 : price;
        }

        public void setPrice(Double price) {
            this.price = price;
        }

        public DataExtension getDataExtension() {
            return dataExtension;
        }

        public void setDataExtension(DataExtension dataExtension) {
            this.dataExtension = dataExtension;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public void Update() {


        }
    }
}
