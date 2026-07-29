package io.pragmatic.ddd.scenario;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.operation.OperationRegistry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter(AccessLevel.PROTECTED)
public class Invoice extends AggregateRoot<Long> {

    private String title;
    private String no;

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return InvoiceBrokenRuleRegistry.INSTANCE;
    }


    @Override
    protected OperationRegistry operationRegistry() {
        return InvoiceOperationRegistry.INSTANCE;
    }
}
