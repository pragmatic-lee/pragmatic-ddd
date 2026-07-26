package io.pragmatic.ddd.base.test2;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

public class InvoiceOperationRegistry extends OperationRegistry {

    public static final EntityOperation UPDATE = EntityOperation.of("UPDATE", "更新发票信息");
    public static final EntityOperation UPDATE_AUDIT_STATUS = EntityOperation.of("UPDATE_AUDIT_STATUS", "更新审核信息");

    public static final InvoiceOperationRegistry INSTANCE = new InvoiceOperationRegistry();

    private InvoiceOperationRegistry() {
    }
}
