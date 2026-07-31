package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.RuleCheckResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RuleCheckResult} 静态工厂与取值测试。
 *
 * @author wizard-lee
 */
class RuleCheckResultTest {

    @Test
    void pass_isSatisfiedWithEmptyParamsAndAutoFormat() {
        RuleCheckResult result = RuleCheckResult.pass();
        assertThat(result.isSatisfy()).isTrue();
        assertThat(result.getParams()).isEmpty();
        assertThat(result.isAutoFormat()).isTrue();
    }

    @Test
    void pass_returnsSameSingletonInstance() {
        assertThat(RuleCheckResult.pass()).isSameAs(RuleCheckResult.pass());
    }

    @Test
    void fail_withoutParams() {
        RuleCheckResult result = RuleCheckResult.fail();
        assertThat(result.isSatisfy()).isFalse();
        assertThat(result.getParams()).isEmpty();
        assertThat(result.isAutoFormat()).isTrue();
    }

    @Test
    void fail_withParams() {
        Object[] params = new Object[]{"name", 18};
        RuleCheckResult result = RuleCheckResult.fail(params);
        assertThat(result.isSatisfy()).isFalse();
        assertThat(result.getParams()).containsExactly("name", 18);
        assertThat(result.isAutoFormat()).isTrue();
    }

    @Test
    void fail_withParamsAndFormatFlag_false() {
        Object[] params = new Object[]{"name"};
        RuleCheckResult result = RuleCheckResult.fail(params, false);
        assertThat(result.isSatisfy()).isFalse();
        assertThat(result.getParams()).containsExactly("name");
        assertThat(result.isAutoFormat()).isFalse();
    }

    @Test
    void fail_withParamsAndFormatFlag_true() {
        Object[] params = new Object[]{"name"};
        RuleCheckResult result = RuleCheckResult.fail(params, true);
        assertThat(result.isAutoFormat()).isTrue();
    }

    @Test
    void params_array_exposesProvidedLength() {
        Object[] params = new Object[]{1, 2, 3};
        RuleCheckResult result = RuleCheckResult.fail(params);
        assertThat(result.getParams()).hasSize(3);
    }
}
