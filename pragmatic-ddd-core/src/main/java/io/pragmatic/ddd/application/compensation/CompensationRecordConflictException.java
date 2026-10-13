package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 补偿记录冲突异常：同一 actionKey 已存在持久化补偿记录（重复执行同一外部需求）。
 * 与 PragmaticException 体系一致，可被 catch (PragmaticException e) 统一兜底。
 *
 * @author wizard-lee
 */
public class CompensationRecordConflictException extends PragmaticException {

    private final transient String actionKey;

    /**
     * 以冲突的幂等键与底层原因构造。
     *
     * @param actionKey 幂等键
     * @param cause     底层冲突原因（主键 / 唯一键冲突）
     */
    public CompensationRecordConflictException(String actionKey, Throwable cause) {
        super("compensation record already exists: " + actionKey, cause);
        this.actionKey = actionKey;
    }

    /** 返回冲突的幂等键。 */
    public String getActionKey() {
        return actionKey;
    }
}
