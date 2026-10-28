# BP 卡：值对象最佳实践（value-object）

> 源：`documentation/best-practices/value-object.md` · 派生时间：2026-10-28 · 框架版本：2.1.0-SNAPSHOT

## 1. 什么是值对象

由属性组合判等的可嵌入数据结构（地址、金额、区间），**不可变**。核心特征是**结构相等**：属性相同即相等，没有独立于属性的身份。

| 类型 | 特征 | 例子 |
| --- | --- | --- |
| 值对象（继承 `ValueObject`/实现 `IValueObject`） | 多字段组合、按结构判等 | 地址、金额、区间 |
| 枚举（实现 `IEnumValue<T,自身>`） | 固定离散常量集合 | 订单状态、支付方式 |

经验法则：有限常量分类用枚举；多字段组合且按结构判等的数据用值对象。

## 2. 值对象 vs 参数对象

| 类型 | 定位 | 特征 |
| --- | --- | --- |
| 值对象 | 领域内可嵌入数据结构 | 判等是领域行为；可持久化（MyBatis JSON 列） |
| 参数对象（`IParamObject`） | 入参载体 | 纯数据容器、`@Data` 即可；不参与持久化、无值语义 |

## 3. 编写规范

### 3.1 继承 `ValueObject` 基类

覆写 `equalityComponents()` 返回参与判等的成分（**顺序敏感**）；`equals`/`hashCode` 由基类以 `final` 提供，子类不可覆盖；`toString` **不是** `final`，需要自定义展示时可在子类覆写（默认实现为「类名 + `equalityComponents()` 数组」）；相等判定要求**精确类匹配**（子类间成分相同也不相等）。

```java
@Override
protected Object[] equalityComponents() {
    return new Object[]{province, city, detail};
}
```

### 3.2 实现 `IValueObject` 标记

经 MyBatis JSON TypeHandler 整体读写 DB JSON 列——**必须实现 `IValueObject`** 才会被自动登记。`ValueObject` 基类已实现 `IValueObject`，继承即满足。

### 3.3 不可变

- 字段不提供公开 setter；判等由基类托管。
- 全参业务构造完成初始化；**构造期不做字段校验**。
- 不可变性由 Lombok 保证：`@Getter`+`@Setter(AccessLevel.PROTECTED)`（仅持久化重建）+`@NoArgsConstructor(PROTECTED)`；**禁用 `@Data`/`@EqualsAndHashCode`/`@Builder`**。

```java
@Getter
@Setter(AccessLevel.PROTECTED)
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

## 4. 校验职责：值对象不做校验

值对象是**纯粹数据载体**，不在内部校验字段（构造期、方法内都不做）。字段合法性统一由**校验规则类**在应用层执行：内部不变量在 `EntityRule` 子类 `init()` 里 `addRule`；外部依赖校验定义校验规则领域服务（`extends IDomainService`）注入 `EntityRule` 执行。详见 `order-rule-pattern.md`。

> 原因：同一值对象在不同上下文中合法性不同（订单金额 `>0`、退款金额 `>=0`），写死在值对象内会耦合固定；统一由规则类按场景校验，值对象保持纯粹。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 提供公开 setter | `@Setter(PROTECTED)` 仅限重建 |
| 覆盖 equals/hashCode | 交给基类，只覆写 `equalityComponents()` |
| 用值对象表达固定离散常量 | 改用枚举 `IEnumValue` |
| 用枚举承载多字段复合数据 | 改用值对象 |
| 用 `@Data`/`@Builder` | 用 `@Getter`+`@Setter(PROTECTED)` |
| 构造期内做字段校验 | 校验交由校验规则类统一执行 |

## 6. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
