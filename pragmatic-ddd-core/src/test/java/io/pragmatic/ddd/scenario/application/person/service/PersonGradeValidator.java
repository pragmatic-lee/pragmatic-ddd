package io.pragmatic.ddd.scenario.application.person.service;

import io.pragmatic.ddd.rules.ICheckRule;
import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.scenario.domain.person.model.Person;

/**
 * 人员等级校验（BUSINESS_RULE 实现）。
 *
 * @author wizard-lee
 */
public class PersonGradeValidator implements ICheckRule<Person> {

    @Override
    public RuleCheckResult check(Person model, Person oldModel) {
        return RuleCheckResult.of(model != null && model.getAge() != null && !model.getAge().isBlank());
    }
}
