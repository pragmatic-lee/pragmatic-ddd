package io.pragmatic.ddd.scenario;

import io.pragmatic.ddd.scenario.boxvalueobject.PersonInitData;
import io.pragmatic.ddd.scenario.boxvalueobject.PersonUpdateData;
import io.pragmatic.ddd.scenario.entity.Person;
import io.pragmatic.ddd.scenario.event.PersonUpdateEvent;
import io.pragmatic.ddd.scenario.entity.enums.Status;
import io.pragmatic.ddd.scenario.rule.PersonEntityRule;
import io.pragmatic.ddd.scenario.rule.validator.BasePersonGradeValidator;
import io.pragmatic.ddd.scenario.rule.validator.BasePersonScoreValidator;
import org.junit.Test;

/**
 * 场景示例：验证人员聚合根的初始化、更新与规则校验行为。
 *
 * @author wizard-lee
 */
public class PersonTest {

    @Test
    public void testCase1() {

        BasePersonScoreValidator personScoreValidator = this.mockScoreValidator();
        BasePersonGradeValidator personGradeValidator = this.mockGradeValidator();

        Person person = this.mockData();

        Boolean validate = person.satisfiesRule(new PersonEntityRule(personScoreValidator,personGradeValidator));
        assert validate;
    }

    @Test
    public void testCase2() {

        BasePersonScoreValidator personScoreValidator = this.mockScoreValidator();
        BasePersonGradeValidator personGradeValidator = this.mockGradeValidator();

        Person person = this.mockData();
        person.update(new PersonUpdateData());
        Boolean validate = person.satisfiesRule(new PersonEntityRule(personScoreValidator, personGradeValidator));

        assert !validate;
        assert person.hasOperation(PersonOperations.UPDATE);

    }

    @Test
    public void testCase3() {

        BasePersonScoreValidator personScoreValidator = this.mockScoreValidator();
        BasePersonGradeValidator personGradeValidator = this.mockGradeValidator();

        Person person = this.mockData();
        person.updateStatus(Status.END);
        Boolean validate1 = person.satisfiesRule(new PersonEntityRule(personScoreValidator, personGradeValidator));


        assert validate1;
        assert person.hasOperation(PersonOperations.UPDATE_STATUS);

    }

    @Test
    public void testCase4() {

        BasePersonScoreValidator personScoreValidator = this.mockScoreValidator();
        BasePersonGradeValidator personGradeValidator = this.mockGradeValidator();

        Person person = this.mockData();
        person.updateStatus(Status.ILLEGAL);
        Boolean validate1 = person.satisfiesRule(new PersonEntityRule(personScoreValidator, personGradeValidator));

        assert !validate1;
        assert person.hasOperation(PersonOperations.UPDATE_STATUS);
    }

    @Test
    public void testOperationCodeAligned() {
        Person person = this.mockData();
        person.update(new PersonUpdateData());

        PersonUpdateEvent event = person.getDomainEvents()
                .stream()
                .filter(e -> e instanceof PersonUpdateEvent)
                .map(e -> (PersonUpdateEvent) e)
                .findFirst()
                .orElseThrow();

        assert PersonOperations.UPDATE.code().equals(event.operationCode);
    }

    private Person mockData() {
        PersonInitData personInitData = new PersonInitData();
        personInitData.setId(1L);
        personInitData.setName("test");
        personInitData.setAge("test");
        personInitData.setEmail("test@test.com");
        personInitData.setPhone("11123231");
        personInitData.setStatus(Status.END);

        return new Person(personInitData);
    }





    private BasePersonGradeValidator mockGradeValidator() {
        return new BasePersonGradeValidator(){

            @Override
            protected boolean validate(Person model, Person oldModel) {
                return true;
            }

        };
    }

    private BasePersonScoreValidator mockScoreValidator() {
        return new BasePersonScoreValidator(){

            @Override
            protected boolean validate(Person model, Person oldModel) {
                return true;
            }

        };
    }
}
