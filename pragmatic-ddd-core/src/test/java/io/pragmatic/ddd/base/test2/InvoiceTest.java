package io.pragmatic.ddd.base.test2;

import io.pragmatic.ddd.base.BrokenRule;
import org.junit.Test;

import java.util.List;

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
