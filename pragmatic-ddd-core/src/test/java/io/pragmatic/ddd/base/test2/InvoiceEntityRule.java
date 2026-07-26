package io.pragmatic.ddd.base.test2;

import io.pragmatic.ddd.rules.EntityRule;
import org.apache.commons.lang3.StringUtils;

import static io.pragmatic.ddd.base.test2.InvoiceBrokenRuleMessage.*;

public class InvoiceEntityRule extends EntityRule<Invoice> {

    public InvoiceEntityRule() {
        this.addRule(s -> StringUtils.isNotEmpty(s.getTitle()), TITLE_IS_EMPTY_ERROR);
        this.addRule(s -> StringUtils.isNotEmpty(s.getNo()), NO_IS_EMPTY_ERROR);
    }

    @Override
    protected Invoice supplyOldEntity() {
        return null;
    }
}
