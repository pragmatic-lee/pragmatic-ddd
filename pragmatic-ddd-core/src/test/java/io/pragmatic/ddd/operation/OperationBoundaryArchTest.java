package io.pragmatic.ddd.operation;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

/**
 * 架构边界守护（对应设计文档 3.2.1）。
 * <p>把"action 不是值对象"的团队经验固化为机器可检查的硬规则：
 * 1. 实现 {@link IEntityOperation} 的类绝不可能是 enum（杜绝把 action 写成枚举）；
 * 2. {@link OperationRegistry} 的子类必须位于 operation 包（杜绝与 VO 枚举混放）。</p>
 *
 * @author wizard-lee
 */
class OperationBoundaryArchTest {

    private static final JavaClasses IMPORTED =
            new ClassFileImporter().importPackages("io.pragmatic.ddd");

    @Test
    void entityOperationMustNotBeEnum() {
        ArchRule rule = ArchRuleDefinition.classes()
                .that().implement(IEntityOperation.class)
                .should().notBeEnums();
        rule.check(IMPORTED);
    }

    @Test
    void operationRegistrySubclassMustResideInOperationPackage() {
        ArchRule rule = ArchRuleDefinition.classes()
                .that().areAssignableTo(OperationRegistry.class)
                .should().resideInAPackage("..operation..");
        rule.check(IMPORTED);
    }
}
