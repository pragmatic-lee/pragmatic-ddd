package io.pragmatic.ddd.scenario.rule;

import io.pragmatic.ddd.scenario.boxvalueobject.PersonCopyData;
import io.pragmatic.ddd.scenario.entity.Person;
import io.pragmatic.ddd.scenario.entity.enums.Status;
import io.pragmatic.ddd.scenario.rule.validator.BasePersonGradeValidator;
import io.pragmatic.ddd.scenario.rule.validator.BasePersonScoreValidator;
import io.pragmatic.ddd.rules.EntityRule;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import static io.pragmatic.ddd.scenario.rule.PersonBrokenRuleRegistry.*;

public class PersonEntityRule extends EntityRule<Person> {

    public PersonEntityRule(BasePersonScoreValidator personScoreValidator,
                            BasePersonGradeValidator personGradeValidator
    ) {

        super(false);

        this.addRule(s -> StringUtils.isNoneEmpty(s.getName()), NAME_ERROR);
        this.addRule(s -> StringUtils.isNoneEmpty(s.getAge()), AGE_ERROR);
        this.addRule(s -> StringUtils.isNoneEmpty(s.getEmail()), EMAIL_ERROR);
        this.addRule(s -> StringUtils.isNoneEmpty(s.getPhone()), PHONE_ERROR);
        this.addRule(personScoreValidator, PERSON_SCORE_ERROR);
        this.addRule(personGradeValidator, PERSON_SCORE_ERROR);
    }

    @Override
    protected Person supplyOldEntity() {
        return null;
    }
}
