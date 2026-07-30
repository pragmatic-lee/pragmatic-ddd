package io.pragmatic.ddd.rules;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BusinessRule} 注解保留策略与属性读取测试。
 *
 * @author wizard-lee
 */
class BusinessRuleAnnotationTest {

    @BusinessRule(description = "必须为正数", errorCode = "POS", errorMessage = "必须为正")
    void annotatedMethod() {
    }

    @BusinessRule
    void defaultAnnotatedMethod() {
    }

    @Test
    void annotation_isRetainedAtRuntimeAndReadable() throws Exception {
        Method method = BusinessRuleAnnotationTest.class.getDeclaredMethod("annotatedMethod");
        assertThat(method.isAnnotationPresent(BusinessRule.class)).isTrue();
        BusinessRule annotation = method.getAnnotation(BusinessRule.class);
        assertThat(annotation.description()).isEqualTo("必须为正数");
        assertThat(annotation.errorCode()).isEqualTo("POS");
        assertThat(annotation.errorMessage()).isEqualTo("必须为正");
    }

    @Test
    void defaults_areEmpty() throws Exception {
        Method method = BusinessRuleAnnotationTest.class.getDeclaredMethod("defaultAnnotatedMethod");
        BusinessRule annotation = method.getAnnotation(BusinessRule.class);
        assertThat(annotation.description()).isEmpty();
        assertThat(annotation.errorCode()).isEmpty();
        assertThat(annotation.errorMessage()).isEmpty();
    }
}
