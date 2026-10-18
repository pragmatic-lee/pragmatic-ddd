# BP 卡：枚举值对象最佳实践（enum-value）

> 源：`documentation/best-practices/enum-value.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 什么是枚举值对象

`IEnumValue<T, 自身>` 用于表达**固定离散常量集合**（订单状态、支付方式），替代 Java `enum`，规避 MyBatis 持久化痛点：

- Java `enum` 的 `ordinal()`（声明序号）脆弱——插序/删序导致存量数据语义错乱。
- `IEnumValue` 通过 `getValue()` 返回**稳定的业务 code**，持久化写 code，与声明顺序无关。

```java
public interface IEnumValue<T, K extends Enum<?>> {
    T getValue();                              // 持久化/传输用的业务 code
    String getName();                         // 展示名（label）
    default String getDesc() { return getName(); }  // 描述，默认等同展示名
}
```

- `T`：业务 code 类型（持久化到 DB 的值），可为 `String`/`Integer`/`Long` 等任意类型。
- `K`：枚举类型本身。

## 2. 如何定义

实现 `IEnumValue<T, 自身>`，`value` 与 `name` 分离；code 用**数字**（`Integer`）：

```java
public enum OrderStatus implements IEnumValue<Integer, OrderStatus> {
    CREATED(1, "已创建"),
    PAID(2, "已支付"),
    CANCELLED(3, "已取消");

    private final int value;
    private final String name;

    OrderStatus(int value, String name) { this.value = value; this.name = name; }

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

- 数字 code 存 INT 列（`EnumRule(CODE)` 策略），与框架 `CODE` 持久化兼容。
- 静态反查方法（`of`/`from`/`parse` 均可，**不要用 `valueOf`**——与 Java `enum` 内置按名反查冲突）：未知 code 返回 `null`，`of(null)` 也返回 `null` 不抛异常；用于**应用层**把传入 code 转枚举（MyBatis 还原由框架 `EnumValueResolver` 完成，不需此方法）。

## 3. CODE 持久化模式

- 持久化经 `EnumRule(CODE)` 写入 `getValue()` 返回值（默认策略）。
- `value`（code）与 `name`（展示名）分离：code 稳定不可变；`name` 可随业务调整。
- **code 一旦发布不可修改**——存量数据已存在；改 code 等于造新值，应新增常量而非改旧。

### 3.1 code 用 String 还是数字

| 类型 | 优点 | 代价 |
| --- | --- | --- |
| `String`（如 `"PAID"`） | DB 可读、自解释、跨系统友好 | 存储占位大 |
| 数字（`Integer`/`Long`，如 `2`） | 存储紧凑 | DB 可读性差，需映射表 |

框架 `CODE` 策略两者一视同仁——`getValue()` 返回什么就持久化什么（数字→INT 列、字符串→VARCHAR 列）。**无论哪种，发布后都不可修改。**

## 4. 枚举 vs 值对象

有限常量分类用枚举；多字段组合且按结构判等的数据用值对象。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 用 Java `enum` 直接持久化 `ordinal()`/`name()` | 用 `IEnumValue` 的 `getValue()` 业务 code |
| 枚举承载多字段复合数据 | 改用值对象 |
| 用值对象表达固定离散常量 | 改用枚举 |

## 6. 被读于

S4 `ddd-aggregate-create`、S6 `ddd-projection-create`、S7 `ddd-code-review`。
