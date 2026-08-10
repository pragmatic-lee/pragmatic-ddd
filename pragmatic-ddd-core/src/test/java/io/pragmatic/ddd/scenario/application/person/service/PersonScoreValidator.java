package io.pragmatic.ddd.scenario.application.person.service;

import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.rule.validator.BasePersonScoreValidator;

/**
 * 人员积分校验（RULE_VALIDATOR 实现）。
 *
 * @author wizard-lee
 */
public class PersonScoreValidator extends BasePersonScoreValidator {

    @Override
    protected boolean validate(Person model, Person oldModel) {
        return model != null && model.getName() != null && !model.getName().isBlank();
    }
}
