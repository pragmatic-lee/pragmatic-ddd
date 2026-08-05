package io.pragmatic.ddd.acl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 阶段一验证：@ExternalDependency 注解元数据可被反射读取。
 *
 * @author wizard-lee
 */
class ExternalDependencyMetadataTest {

    @ExternalDependency(targetName = "User", type = DependencyType.AGGREGATE,
            description = "查询用户信息，用于规则校验")
    public interface SampleUserDependency extends IDependency {
    }

    @ExternalDependency(targetName = "PaymentGateway")
    public interface SamplePaymentDependency extends IDependency {
    }

    @Test
    void shouldReadAnnotationMetadata() {
        ExternalDependency userMeta = SampleUserDependency.class.getAnnotation(ExternalDependency.class);
        assertThat(userMeta).isNotNull();
        assertThat(userMeta.targetName()).isEqualTo("User");
        assertThat(userMeta.type()).isEqualTo(DependencyType.AGGREGATE);
        assertThat(userMeta.description()).isEqualTo("查询用户信息，用于规则校验");
    }

    @Test
    void shouldDefaultTypeToAggregate() {
        ExternalDependency paymentMeta = SamplePaymentDependency.class.getAnnotation(ExternalDependency.class);
        assertThat(paymentMeta).isNotNull();
        assertThat(paymentMeta.targetName()).isEqualTo("PaymentGateway");
        assertThat(paymentMeta.type()).isEqualTo(DependencyType.AGGREGATE);
        assertThat(paymentMeta.description()).isEmpty();
    }

    @Test
    void dependencyInterfaceShouldBeMarked() {
        assertThat(IDependency.class.isAssignableFrom(SampleUserDependency.class)).isTrue();
    }
}
