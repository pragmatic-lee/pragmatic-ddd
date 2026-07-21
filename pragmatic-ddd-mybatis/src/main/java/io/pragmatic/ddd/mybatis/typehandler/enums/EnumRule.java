package io.pragmatic.ddd.mybatis.typehandler.enums;

/**
 * 枚举持久化策略：决定数据库列里存什么。
 * <ul>
 *   <li>{@link #NAME} —— 存枚举程序名 {@code Enum.name()}</li>
 *   <li>{@link #ORDINAL} —— 存声明序号 {@code Enum.ordinal()}</li>
 *   <li>{@link #CODE} —— 存业务 code（{@code IEnumValue.getValue()}）</li>
 *   <li>{@link #LABEL} —— 存展示名（{@code IEnumValue.getName()}）</li>
 * </ul>
 * 对应设计文档 Step 3（提案 §5.2）。
 */
public enum EnumRule {
    NAME, ORDINAL, CODE, LABEL
}
