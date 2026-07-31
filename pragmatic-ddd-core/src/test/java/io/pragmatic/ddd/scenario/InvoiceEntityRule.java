package io.pragmatic.ddd.scenario;

import io.pragmatic.ddd.base.RuleCheckResult;
import io.pragmatic.ddd.rules.EntityRule;
import org.apache.commons.lang3.StringUtils;

import static io.pragmatic.ddd.scenario.InvoiceBrokenRuleRegistry.*;

public class InvoiceEntityRule extends EntityRule<Invoice> {

    public InvoiceEntityRule() {
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNotEmpty(s.getTitle())), TITLE_IS_EMPTY_ERROR);
        this.addRule((s, old) -> RuleCheckResult.of(StringUtils.isNotEmpty(s.getNo())), NO_IS_EMPTY_ERROR);
    }
}
