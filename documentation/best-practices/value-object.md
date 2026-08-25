# 值对象最佳实践

> 本文档介绍 Pragmatic DDD 中值对象的设计与编码最佳实践：什么是值对象、与枚举/参数对象/实体的取舍、编写规范与常见反模式。前置阅读：[聚合设计原则](./aggregate-design.md)。

## 1. 什么是值对象

值对象是由属性组合判等的可嵌入数据结构（如地址、金额、区间），不可变。核心特征是**结构相等**：两个值对象只要属性相同即相等，没有独立于属性的身份。

与[普通实体](./entity-design.md)（`AbstractEntity`）对比：实体的等同性由 `entityId` 决定，字段变化不改变身份；值对象没有身份，判等即比属性。

值对象是**纯粹的数据载体**：只负责承载字段值，**不在内部校验字段**——字段值的合法性统一由校验规则类执行（见 [§5](#5-校验职责值对象不做校验)）。

## 2. 值对象 vs 枚举

| 类型 | 特征 | 例子 |
| --- | --- | --- |
| 值对象（继承 `ValueObject` / 实现 `IValueObject`） | 由多个字段组合、按结构判等的数据 | 地址、金额、区间 |
| 枚举（实现 `IEnumValue<T, 自身>`） | 固定离散常量集合 | 订单状态、支付方式 |

**经验法则**：能用有限个常量表达的分类用枚举；需要由多个字段组合且按结构判等的数据用值对象。枚举的定义与持久化规范见 [枚举值对象最佳实践](./enum-value.md)。

## 3. 值对象 vs 参数对象

| 类型 | 定位 | 特征 |
| --- | --- | --- |
| 值对象 | 领域内的可嵌入数据结构 | 判等是领域行为；可持久化（MyBatis JSON 列） |
| 参数对象（`IParamObject`） | 入参载体 | 纯数据容器、`@Data` 即可；不参与持久化、无值语义 |

## 4. 编写规范

### 4.1 继承 `ValueObject` 基类

结构相等用 `ValueObject` 基类，覆写 `equalityComponents()` 返回参与判等的成分（**顺序敏感**）。`equals` / `hashCode` / `toString` 由基类 `final` 提供，子类不可覆盖；相等判定要求**精确类匹配**（子类间即使成分相同也不相等）。

```java
@Override
protected Object[] equalityComponents() {
    return new Object[]{province, city, detail};
}
```

### 4.2 实现 `IValueObject` 标记

复杂值对象通过 MyBatis JSON TypeHandler 整体读写数据库 JSON 列——**必须实现 `IValueObject` 标记接口**才会被自动登记。`ValueObject` 基类已实现 `IValueObject`，继承基类即自动满足标记。

### 4.3 不可变

- 字段不提供公开 setter，判等由 `ValueObject` 基类托管
- 通过全参业务构造完成初始化；**构造期不做字段校验**，字段值的合法性由校验规则类统一校验（见 §5）
- 不可变性由使用者保证：Lombok 约定 `@Getter` + `@Setter(AccessLevel.PROTECTED)`（仅供持久化重建）+ `@NoArgsConstructor(access = AccessLevel.PROTECTED)`；**禁用 `@Data` / `@EqualsAndHashCode` / `@Builder`**

### 4.4 示例

```java
@Getter
@Setter(AccessLevel.PROTECTED)          // 仅供 MyBatis JSON 重建
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends ValueObject {

    private String province;
    private String city;
    private String detail;

    public Address(String province, String city, String detail) {
        this.province = province;
        this.city = city;
        this.detail = detail;
    }

    @Override
    protected Object[] equalityComponents() {
        return new Object[]{province, city, detail};
    }
}
```

`new Address("浙江", "杭州", "xxx").equals(new Address("浙江", "杭州", "xxx"))` 为 `true`。

## 5. 校验职责：值对象不做校验

值对象是**纯粹的数据载体**，只负责承载字段值，**不在内部校验字段**（构造期、方法内都不做）。

字段值的合法性校验统一由**校验规则类**在应用层触发执行：

- **内部不变量**：在 `EntityRule` 子类的 `init()` 里 `addRule` 编写
- **外部依赖校验**：定义校验规则领域服务（`extends IDomainService`），注入 `EntityRule` 执行

详见 [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)。

**为什么**：同一值对象在不同业务上下文中合法性约束可能不同（如订单金额要求 `> 0`，退款金额要求 `>= 0`）。把校验写死在值对象内部会耦合固定、无法按场景复用；统一由规则类按场景校验，值对象保持纯粹、可预测。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 值对象提供公开 setter | 破坏不可变性 | `@Setter(PROTECTED)` 仅限持久化重建路径 |
| 值对象覆盖 equals/hashCode | 与 `ValueObject` 基类 final 冲突 | 交给基类，只覆写 `equalityComponents()` |
| 用值对象表达固定离散常量 | 大量实例化、判断繁琐 | 改用枚举 `IEnumValue` |
| 用枚举承载多字段复合数据 | 语义丢失、判等困难 | 改用值对象 |
| 用 `@Data` / `@Builder` | 破坏结构判等 | 用 `@Getter` + `@Setter(PROTECTED)` |
| 值对象构造期内做字段校验 | 校验逻辑固化在数据载体、难以按场景调整 | 校验交由校验规则类统一执行（见 §5） |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根中的值对象使用与 Lombok 约定
- [普通实体设计](./entity-design.md)：有身份的子实体
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)：值对象字段校验的统一执行
- [枚举值对象最佳实践](./enum-value.md)：`IEnumValue` 的定义与 CODE 持久化
- [核心：领域建模](../core/domain-modeling.md)：`ValueObject` / `IValueObject` / `IEnumValue` 基类机制详解
- [MyBatis 集成](../integration/mybatis.md)：JSON TypeHandler 与 `IValueObject` 登记
