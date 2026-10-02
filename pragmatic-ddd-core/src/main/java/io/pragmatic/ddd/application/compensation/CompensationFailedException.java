package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.PragmaticException;

import java.util.List;

/**
 * 补偿失败异常：正向已执行、逆向补偿最终失败，需中继重试或人工介入。
 * 与 PragmaticException 体系一致，可被 catch (PragmaticException e) 统一兜底。
 *
 * @author wizard-lee
 */
public class CompensationFailedException extends PragmaticException {

    private final transient List<CompensationFailure> failures;

    /**
     * 以补偿失败明细与触发补偿的原始异常构造。
     *
     * @param failures 补偿失败明细
     * @param cause    触发补偿的原始失败
     */
    public CompensationFailedException(List<CompensationFailure> failures, Throwable cause) {
        super("compensation failed: " + failures.size() + " action(s)", cause);
        this.failures = List.copyOf(failures);
    }

    /** 返回全部补偿失败明细。 */
    public List<CompensationFailure> getFailures() {
        return failures;
    }
}
