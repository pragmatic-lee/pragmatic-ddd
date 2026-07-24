package io.pragmatic.ddd.base.test2;

import io.pragmatic.ddd.base.test2.action.PersonAction;
import io.pragmatic.ddd.base.test2.boxvalueobject.PersonInitData;
import io.pragmatic.ddd.base.test2.boxvalueobject.PersonUpdateData;
import io.pragmatic.ddd.base.test2.entity.Person;
import io.pragmatic.ddd.base.test2.entity.enums.Status;
import io.pragmatic.ddd.base.test2.rule.PersonEntityRule;
import io.pragmatic.ddd.base.test2.rule.validator.BasePersonGradeValidator;
import io.pragmatic.ddd.base.test2.rule.validator.BasePersonScoreValidator;
import org.junit.Test;

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
        assert person.hasAction(PersonAction.UPDATE_ACTION);

    }

    @Test
    public void testCase3() {

        BasePersonScoreValidator personScoreValidator = this.mockScoreValidator();
        BasePersonGradeValidator personGradeValidator = this.mockGradeValidator();

        Person person = this.mockData();
        person.updateStatus(Status.END);
        Boolean validate1 = person.satisfiesRule(new PersonEntityRule(personScoreValidator, personGradeValidator));


        assert validate1;
        assert person.hasAction(PersonAction.UPDATE_ACTION);

    }

    @Test
    public void testCase4() {

        BasePersonScoreValidator personScoreValidator = this.mockScoreValidator();
        BasePersonGradeValidator personGradeValidator = this.mockGradeValidator();

        Person person = this.mockData();
        person.updateStatus(Status.ILLEGAL);
        Boolean validate1 = person.satisfiesRule(new PersonEntityRule(personScoreValidator, personGradeValidator));

        assert !validate1;
        assert person.hasAction(PersonAction.UPDATE_STATUS_ACTION);
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
            protected boolean validate(Person model) {
                return true;
            }

        };
    }

    private BasePersonScoreValidator mockScoreValidator() {
        return new BasePersonScoreValidator(){

            @Override
            protected boolean validate(Person model) {
                return true;
            }

        };
    }
}
