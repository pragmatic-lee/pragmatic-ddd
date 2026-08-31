package io.pragmatic.ddd.scenario.application.person.rule;

import io.pragmatic.ddd.scenario.domain.person.rule.PersonEntityRule;

/**
 * 人员规则表组装：把各 BUSINESS_RULE 串成聚合规则表。
 *
 * @author wizard-lee
 */
public class PersonRuleAssembler {

    public PersonEntityRule build() {
        return new PersonEntityRule();
    }
}
