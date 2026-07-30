package io.pragmatic.ddd.application;

import io.pragmatic.ddd.base.BrokenRule;

import java.util.List;

/**
 * 命令试跑（Dry-run）结果：passed 表示是否通过全部业务校验，brokenRules 为未通过时的规则违反明细。
 * 对应设计文档《应用服务层 Try-run（Dry-run）能力支持》5.1 节。
 *
 * @author wizard-lee
 */
public record DryRunResult(boolean passed, List<BrokenRule> brokenRules) {

    /** 构造校验通过的试跑结果。 */
    public static DryRunResult pass() {
        return new DryRunResult(true, List.of());
    }

    /** 构造校验未通过的试跑结果，携带规则违反明细。 */
    public static DryRunResult reject(List<BrokenRule> brokenRules) {
        return new DryRunResult(false, List.copyOf(brokenRules));
    }
}
