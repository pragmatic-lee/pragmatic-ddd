package io.pragmatic.ddd.application.compensation;

/**
 * TCC Confirm 扩展契约：实现本接口的补偿动作，会在本地提交成功后收到确认回调。
 * 未实现本接口的动作不参与 Confirm 阶段。
 *
 * @param <T> 正向产出类型
 * @author wizard-lee
 */
public interface IConfirmableAction<T> extends ICompensableAction<T> {

    /**
     * 正向成功且本地提交成功后的收尾动作（TCC 的 Confirm），必须幂等。
     *
     * @param result 正向产出
     */
    void confirm(T result);
}
