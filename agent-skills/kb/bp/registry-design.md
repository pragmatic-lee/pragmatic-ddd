# BP 卡：规则注册表（registry-design）

> 源：`documentation/best-practices/registry-design.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

规则注册表（`BrokenRuleRegistry`）集中声明校验消息码（`MessageCode`），让多个规则容器共享消息、避免重复与漂移。`getRuleDescription(key)` 未注册返回**空串**（只影响描述，不影响 code）。

## 2. 命名与包

- 注册表：`{Agg}BrokenRuleRegistry extends BrokenRuleRegistry`，放 `domain/{agg}/rule/`。
- 消息码：`public static final MessageCode`，`localCode` 全大写下划线无前缀。

## 3. 骨架

```java
public class OrderBrokenRuleRegistry extends BrokenRuleRegistry {
    public static final MessageCode ORDER_CONFIRMED       = MessageCode.of("ORDER_CONFIRMED", "订单已确认");
    public static final MessageCode ORDER_AMOUNT_EXCEED   = MessageCode.of("ORDER_AMOUNT_EXCEED", "订单金额超阈值");
    public static final MessageCode CUSTOMER_PERMISSION   = MessageCode.of("CUSTOMER_PERMISSION", "客户无权限，操作人 [%s]");
    public static final MessageCode ORDER_NOT_EXISTS     = MessageCode.of("ORDER_NOT_EXISTS", "订单不存在");

    private OrderBrokenRuleRegistry() {}

    public static final OrderBrokenRuleRegistry INSTANCE = new OrderBrokenRuleRegistry();
}
```

## 4. 硬约束（对照 `hard-constraints.md`）

- 注册表子类必须 `public`（硬约束 1，测试内也须 `public static nested class`）。
- `MessageCode.localCode` 在注册表内唯一；`code` 与常量字段名完全一致（硬约束 2）。
- 占位支持 `%s`/`%d`：`getRuleDescription(Object[])` 用动态参数填充。
- 子类 `private` 构造 + `INSTANCE` 单例。

## 5. 被读于

S4 `ddd-aggregate-create`（步骤 3）、S7 `ddd-code-review`。
