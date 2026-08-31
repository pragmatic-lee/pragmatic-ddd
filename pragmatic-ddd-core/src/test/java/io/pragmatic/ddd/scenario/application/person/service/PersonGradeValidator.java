package io.pragmatic.ddd.scenario.application.person.service;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.rule.validator.BasePersonGradeValidator;

/**
 * 人员等级校验（BUSINESS_RULE 实现）。
 *
 * @author wizard-lee
 */
public class PersonGradeValidator extends BasePersonGradeValidator {

    @Override
    protected boolean validate(Person model, Person oldModel) {
        return model != null && model.getAge() != null && !model.getAge().isBlank();
    }
}
