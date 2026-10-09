package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

/**
 * 补偿范围：一次应用服务调用内登记的"可补偿外部副作用"集合。
 * 正常结束视为成功不补偿；判定为失败则按正向逆序对"已执行"的动作执行补偿。
 *
 * @author wizard-lee
 */
public interface ICompensationScope extends AutoCloseable {

    /**
     * 执行并登记一条补偿命令：仅当正向执行成功才进入待补偿集合。
     *
     * @param command 待执行补偿命令
     * @param <A>     聚合类型
     * @param <REQ>   需求类型
     * @param <R>     正向产出类型
     * @return 正向执行产出
     */
    <A extends AggregateRoot<?>, REQ extends IExternalRequirement, R> R execute(
            CompensationCommand<A, REQ, R> command);

    /** 标记本范围成功结束：不再触发任何补偿。 */
    void commit();

    /**
     * 逆序补偿本范围内"已执行"的动作。
     * 全部补偿成功时方法正常返回（调用方应重抛原始业务异常）；
     * 存在补偿失败时抛出 CompensationFailedException。
     *
     * @param cause 触发补偿的原始失败
     */
    void rollback(Throwable cause);

    /**
     * 关闭范围：仅清理登记项，不触发补偿（补偿由 commit / rollback 决定）。
     * 实现不得抛出异常：close 的职责是清理，任何异常都会干扰补偿结果与
     * 原始异常的传播，CompensationTemplate 会捕获并记录其异常后忽略。
     */
    @Override
    void close();
}
