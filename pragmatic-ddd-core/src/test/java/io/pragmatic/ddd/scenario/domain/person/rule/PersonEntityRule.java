package io.pragmatic.ddd.scenario.domain.person.rule;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.rules.EntityRule;
import org.apache.commons.lang3.StringUtils;

import static io.pragmatic.ddd.scenario.domain.person.rule.PersonBrokenRuleRegistry.*;

public class PersonEntityRule extends EntityRule<Person> {

    public PersonEntityRule() {
        super(false);

        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getName())), NAME_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getAge())), AGE_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getEmail())), EMAIL_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNoneEmpty(s.getPhone())), PHONE_ERROR);
    }
}
