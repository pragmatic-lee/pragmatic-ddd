package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.base.IRule;
import io.pragmatic.ddd.base.MessageCode;

/**
 * 试跑测试专用规则夹具：按构造入参决定校验结论，未通过时向聚合根追加指定规则违反。
 */
public class DryRunRule implements IRule<DryRunAggregate> {

    private final boolean satisfied;
    private final MessageCode messageCode;

    public DryRunRule(boolean satisfied, MessageCode messageCode) {
        this.satisfied = satisfied;
        this.messageCode = messageCode;
    }

    @Override
    public boolean satisfiesRule(DryRunAggregate model) {
        if (satisfied) {
            return true;
        }
        model.addBrokenRule(messageCode);
        return false;
    }
}
