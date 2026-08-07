package io.pragmatic.ddd.scenario;

import io.pragmatic.ddd.base.BrokenRule;
import org.junit.Test;

import java.util.List;

/**
 * 场景示例：验证发票聚合根的领域规则校验行为。
 *
 * @author wizard-lee
 */
public class InvoiceTest {

    @Test
    public void invoiceTest1(){

        Invoice invoice = new Invoice();

        boolean b = invoice.satisfiesRule(new InvoiceEntityRule());

        assert !b;

        List<BrokenRule> brokenRules = invoice.getBrokenRules();

        assert !brokenRules.isEmpty();
    }
}
