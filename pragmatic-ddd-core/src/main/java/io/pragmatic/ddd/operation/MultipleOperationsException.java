package io.pragmatic.ddd.operation;

/**
 * 一次工作单元内记录了多个不同因果操作时抛出。
 * 表达「一个命令只能有一个因果起源」的不变量被破坏。
 *
 * @author wizard-lee
 */
public class MultipleOperationsException extends OperationException {

    /** 以冲突的两个操作 code 构造。 */
    public MultipleOperationsException(String firstCode, String secondCode) {
        super("一次工作单元内只允许一个因果操作，已记录 [" + firstCode + "]，又收到 [" + secondCode + "]；"
                + "复合意图请命名为新的原子操作，或拆分为多个命令（saga）");
    }
}
