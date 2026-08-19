# 枚举值对象最佳实践

> 本文档介绍 Pragmatic DDD 中枚举值对象（`IEnumValue`）的设计与编码最佳实践：为什么用它替代 Java `enum`、如何定义、`CODE` 持久化模式、与值对象的取舍。前置阅读：[值对象最佳实践](./value-object.md)。

## 1. 什么是枚举值对象

枚举值对象（`IEnumValue<T, 自身>`）用于表达**固定离散常量集合**（如订单状态、支付方式），替代 Java `enum`，规避 MyBatis 持久化的痛点：

- Java `enum` 的 `ordinal()`（声明序号）脆弱——插序、删序会导致数据库里的存量数据语义错乱。
- `IEnumValue` 通过 `getValue()` 返回**稳定的业务 code**，持久化写 code，与声明顺序无关。

接口契约：

```java
public interface IEnumValue<T, K extends Enum<?>> {
    T getValue();          // 持久化 / 传输用的业务 code
    String getName();      // 展示名（label），用于下拉、日志、可视化
    default String getDesc() { return getName(); }  // 描述，默认等同展示名
}
```

| 类型参数 | 含义 |
| --- | --- |
| `T` | 业务 code 类型（持久化到 DB 的值） |
| `K` | 枚举类型本身 |

> `getValue()` 的业务 code 类型 `T` 可以是 `String`、`Integer`、`Long` 等任意类型——框架 `CODE` 策略与类型无关，`getValue()` 返回什么就持久化什么。

## 2. 如何定义

实现 `IEnumValue<T, 自身>` 接口，`value` 与 `name` 分离。code 用**数字**（`Integer`）：

```java
import java.util.Arrays;
import java.util.Objects;

public enum OrderStatus implements IEnumValue<Integer, OrderStatus> {
    CREATED(1, "已创建"),
    PAID(2, "已支付"),
    CANCELLED(3, "已取消");

    private final int value;
    private final String name;

    OrderStatus(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override public Integer getValue() { return value; }
    @Override public String getName() { return name; }

    /** 由业务 code 反查枚举；未知 code 返回 null。 */
    public static OrderStatus of(Integer value) {
        return Arrays.stream(values())
                .filter(status -> Objects.equals(status.getValue(), value))
                .findFirst()
                .orElse(null);
    }
}
```

> 数字 code 存储为 INT 列（`EnumRule(CODE)` 策略），与框架的 `CODE` 持久化完全兼容，见 [MyBatis 集成](../integration/mybatis.md) 示例。

### 2.1 由值转枚举：静态快捷方法

给枚举加一个静态快捷方法，把业务 code（`getValue()` 的返回值）转回枚举对象，避免业务代码里到处手写 `for` 遍历或 `switch`：

```java
OrderStatus status = OrderStatus.of(2);   // → OrderStatus.PAID
```

- **命名**：`of` / `from` / `parse` 均可，团队统一即可；**不要用 `valueOf`**——会与 Java `enum` 内置的按名称反查 `valueOf(String)` 冲突。
- **返回语义**：未知 code 返回 `null`，调用方使用前需判空；`Objects.equals` 对 `null` 入参同样安全，`of(null)` 直接返回 `null`，不会抛异常。
- **与框架反序列化的关系**：MyBatis 从 DB 还原枚举由框架 `EnumValueResolver` 完成，不需要此方法；此方法用于**应用层**把传入的 code（如请求参数、消息载荷）转成枚举。

## 3. CODE 持久化模式

- 持久化经 `EnumRule(CODE)` 写入 `getValue()` 的返回值（默认策略，见 [MyBatis 集成](../integration/mybatis.md)）。
- `value`（业务 code）与 `name`（展示名）分离：code 稳定、不可变；`name` 可随业务调整。
- **code 一旦发布不可修改**——数据库已存量数据；修改 code 等于造新值，应新增常量而非改旧。

### 3.1 code 用 String 还是数字？

| 类型 | 优点 | 代价 |
| --- | --- | --- |
| `String`（如 `"PAID"`） | DB 中可读性强、自解释；对外暴露、跨系统传输友好 | 存储占位相对大 |
| 数字（`Integer` / `Long`，如 `2`） | 存储紧凑、占位小；适合内部状态机 | DB 中可读性差，需枚举映射表对照 |

框架 `CODE` 策略对两者一视同仁——`getValue()` 返回什么就持久化什么（数字存 INT 列、字符串存 VARCHAR 列）。**无论选哪种，发布后都不可修改。**

## 4. 枚举 vs 值对象

| 类型 | 特征 | 例子 |
| --- | --- | --- |
| 枚举（实现 `IEnumValue<T, 自身>`） | 固定离散常量集合 | 订单状态、支付方式 |
| 值对象（继承 `ValueObject` / 实现 `IValueObject`） | 由多个字段组合、按结构判等的数据 | 地址、金额、区间 |

**经验法则**：能用有限个常量表达的分类用枚举；需要由多个字段组合且按结构判等的数据用值对象。

## 5. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 用 Java `enum` 直接持久化 `ordinal()` / `name()` | 插序 / 改名导致存量数据语义错乱 | 用 `IEnumValue` 的 `getValue()` 业务 code |
| 枚举承载多字段复合数据 | 语义丢失、判等困难 | 改用值对象 |
| 用值对象表达固定离散常量 | 大量实例化、判断繁琐 | 改用枚举 |

---

## 下一步

- [值对象最佳实践](./value-object.md)：值对象与枚举的取舍
- [聚合设计原则](./aggregate-design.md)
- [核心：领域建模](../core/domain-modeling.md)：`IEnumValue` / `ValueObject` 机制详解
- [MyBatis 集成](../integration/mybatis.md)：`EnumRule(CODE)` 持久化
