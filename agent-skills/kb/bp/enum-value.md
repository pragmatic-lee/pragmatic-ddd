# BP 卡：枚举值对象（enum-value）

> 源：`documentation/best-practices/enum-value.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

枚举值对象封装「可持久化、可校验、可展示」的枚举语义：业务 code（`getValue`，数字或字符串）+ 展示名（`getName`）+ 描述（`getDesc`）。领域内部用 `int` code 持久化；**出入参/投影层见 `hard-constraints.md` E1~E3，使用 `Integer`/`String` 基础类型**。

## 2. 实现契约

```java
public enum OrderStatus implements IEnumValue<Integer, OrderStatus> {
    CREATED(1, "已创建"),
    PAID(2, "已支付"),
    SHIPPED(3, "已发货"),
    CANCELLED(4, "已取消");

    @Getter
    private final Integer value;
    @Getter
    private final String name;

    OrderStatus(Integer value, String name) {
        this.value = value;
        this.name = name;
    }

    public static Optional<OrderStatus> fromValue(Integer value) {
        return Arrays.stream(values()).filter(o -> o.value.equals(value)).findFirst();
    }
}
```

## 3. 关键约定

- 实现 `IEnumValue<Integer, 自身>`（`code` 类型 T 任意：数字/String；业务字段通常 `int`）。
- `getValue()` 业务 code、`getName()` 展示名、`getDesc()` 默认等同 name。
- 字段 `private final` + 全参构造；提供 `fromValue` 反查。
- 持久化：`@JSONField(name = "status", serializeUsing = ..., deserializeUsing = ...)` 配置 code 与枚举互转（详见源文档）。

## 4. 出入参层（硬约束 E1~E3）

- Input / 查询条件对象的枚举字段统一用 `Integer`（不暴露枚举类型）。
- 投影读模型的枚举字段统一用 `int`/`Integer`/`String`。
- 聚合内部状态仍用枚举类型（如 `OrderStatus status`）。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| Input 用枚举类型字段 | 用 `Integer`，聚合内转枚举 |
| 枚举 `code` 用字符串但聚合该字段是数字 | code 类型跟随聚合字段定义 |
| 投影里保留枚举类型 | 投影用基础类型 |

## 6. 被读于

S4 `ddd-aggregate-create`（步骤 2 状态字段）、S7 `ddd-code-review`。
