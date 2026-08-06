package io.pragmatic.ddd.acl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ACL 异常继承层次契约测试，对齐 base 包 ExceptionHierarchyTest 的统一 catch 能力约束。
 *
 * @author wizard-lee
 */
class AclExceptionHierarchyTest {

    @Test
    void conversionException_unifiedCatchable() {
        AclConversionException ex = new AclConversionException("转换失败", new RuntimeException());
        assertThat(ex)
                .isInstanceOf(AclException.class)
                .isInstanceOf(io.pragmatic.ddd.base.PragmaticException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void communicationException_unifiedCatchable() {
        AclCommunicationException ex = new AclCommunicationException(new RuntimeException());
        assertThat(ex)
                .isInstanceOf(AclException.class)
                .isInstanceOf(io.pragmatic.ddd.base.PragmaticException.class)
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void conversionException_preservesCause() {
        RuntimeException cause = new RuntimeException("root");
        AclConversionException ex = new AclConversionException("转换失败", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void communicationException_preservesCause() {
        RuntimeException cause = new RuntimeException("root");
        AclCommunicationException ex = new AclCommunicationException("通信失败", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
