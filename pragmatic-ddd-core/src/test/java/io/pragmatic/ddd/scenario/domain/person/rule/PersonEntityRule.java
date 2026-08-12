package io.pragmatic.ddd.scenario.domain.person.rule;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.rule.validator.BasePersonGradeValidator;
import io.pragmatic.ddd.scenario.domain.person.rule.validator.BasePersonScoreValidator;
import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.rules.EntityRule;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import static io.pragmatic.ddd.scenario.domain.person.rule.PersonBrokenRuleRegistry.*;

public class PersonEntityRule extends EntityRule<Person> {

    public PersonEntityRule(BasePersonScoreValidator personScoreValidator,
                            BasePersonGradeValidator personGradeValidator
    ) {

        super(false);

        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getName())), NAME_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getAge())), AGE_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getEmail())), EMAIL_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getPhone())), PHONE_ERROR);
        this.addRule(personScoreValidator, PERSON_SCORE_ERROR);
        this.addRule(personGradeValidator, PERSON_SCORE_ERROR);
    }
}
