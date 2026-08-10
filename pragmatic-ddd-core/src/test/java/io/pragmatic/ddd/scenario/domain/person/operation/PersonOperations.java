package io.pragmatic.ddd.scenario.domain.person.operation;

import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

/**
 * Person 实体支持的操作集合（对应设计文档步骤 6）。
 * <p>继承 {@link OperationRegistry}，由基类反射自动注册声明的 static EntityOperation 常量，
 * 无需 registerActions() / register() 模板方法。NEW / DELETE 由基类提供。</p>
 *
 * @author wizard-lee
 */
public class PersonOperations extends OperationRegistry {

    public static final EntityOperation START = EntityOperation.of("startAction", "启动");
    public static final EntityOperation END = EntityOperation.of("endAction", "停止");
    public static final EntityOperation UPDATE = EntityOperation.of("updateAction", "更新");
    public static final EntityOperation UPDATE_STATUS = EntityOperation.of("updateStatusAction", "更新状态");
    public static final EntityOperation FREEZE = EntityOperation.of("freezeAction", "冻结");
    public static final EntityOperation UNFREEZE = EntityOperation.of("unfreezeAction", "解冻");
    public static final EntityOperation BIND_EMAIL = EntityOperation.of("bindEmailAction", "绑定邮箱");
    public static final EntityOperation BIND_PHONE = EntityOperation.of("bindPhoneAction", "绑定手机");
    public static final EntityOperation ASSIGN_DEPT = EntityOperation.of("assignDeptAction", "归属部门");
    public static final EntityOperation TAG = EntityOperation.of("tagAction", "打标签");
    public static final EntityOperation ARCHIVE = EntityOperation.of("archiveAction", "归档");

    public static final PersonOperations INSTANCE = new PersonOperations();
}
