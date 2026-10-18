# BP 卡：值对象（value-object）

> 源：`documentation/best-practices/value-object.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

值对象是无独立身份、靠属性值判等的不可变数据片段（地址、金额、区间、坐标）。不可变性是其核心契约：不可变对象才能在跨线程/事件流中安全传递，才能直接 JSON 序列化；任何改变都应返回新实例。

## 2. 与实体的区别

| 维度 | 值对象 | 实体 |
| --- | --- | --- |
| 身份 | 无独立 id，靠属性值判等 | 有实体 id |
| 生命周期 | 随父聚合存在 | 独立存在 |
| 判等 | `equals` 比全部业务字段 | `equals` 比 id |
| 修改 | 返回新实例（不可变） | 原地改状态 |

## 3. 标记与结构

- 继承 `ValueObject` 基类（已实现 `IValueObject` 标记接口）：覆写 `equalityComponents()` 供字段判等，由基类的 `final` `equals`/`hashCode`/`toString` 调用，不覆盖 `Object.equals`。
- 结构：`@Getter` + `@Setter(AccessLevel.PROTECTED)`（仅供 MyBatis JSON 重建）+ `@NoArgsConstructor(access = AccessLevel.PROTECTED)` + 全参业务构造 + `equalityComponents()`。
- **禁用 Lombok `@Data` / `@EqualsAndHashCode` / `@Builder`**：会生成可变 setter 与默认 `equals`（基于引用），破坏不可变与值判等语义。

## 4. 字段设计

- 用不可变类型：`BigDecimal` / `String` / 值对象嵌套；禁用 `Date` / `Calendar`（可变），用 `LocalDateTime` / `Instant`。
- 枚举字段存 `code`（见 `enum-value.md`）。
- 集合字段用不可变集合（`List.of` / `Collections.unmodifiableList`）。
- 值对象**不在内部做字段校验**（构造期、方法内都不做）：字段合法性由校验规则类统一执行（见 [§5](#5-校验职责值对象不做校验)）。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| `@Data` 值对象 | `@Getter` + `@Setter(PROTECTED)` + `@NoArgsConstructor(PROTECTED)` + 全参业务构造 + `equalityComponents()` |
| 暴露 setter / 可变字段 | 不可变，改值返回新实例 |
| `equals` 用 Object 默认 | 实现 `equalityComponents()` |
| 用 `Date` | `LocalDateTime` / `Instant` |
| 值对象在构造器/方法内做字段校验 | 收口在校验规则类（`EntityRule` / `IDomainService`） |

## 6. 被读于

S4 `ddd-aggregate-create`（步骤 2 值对象）、S7 `ddd-code-review`。
