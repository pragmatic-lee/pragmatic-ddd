package io.pragmatic.ddd.scenario.application.person.rule;

import io.pragmatic.ddd.scenario.application.person.service.PersonGradeValidator;
import io.pragmatic.ddd.scenario.application.person.service.PersonScoreValidator;
import io.pragmatic.ddd.scenario.domain.person.rule.PersonEntityRule;

/**
 * 人员规则表组装：把各 RULE_VALIDATOR 串成聚合规则表。
 *
 * @author wizard-lee
 */
public class PersonRuleAssembler {

    public PersonEntityRule build() {
        return new PersonEntityRule(new PersonScoreValidator(), new PersonGradeValidator());
    }
}
