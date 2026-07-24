package io.pragmatic.ddd.application;

import java.util.function.BiFunction;

/**
 * FieldResolver 适配器工厂。
 *
 * <p><b>核心价值</b>：将同一个 {@link FieldCalculator} 适配到不同的 Command DTO / 实体组合，
 * 实现"一处定义计算逻辑，多处复用"。框架承担"适配"这件事，开发者只写计算逻辑。</p>
 *
 * <p>v3 增强：extractor 由 {@code Function<C,T>} 升级为 {@code BiFunction<C,E,T>}，
 * 可以同时从 Command DTO 与实体两处提取数据；实体在 create 场景为 null，
 * 由 {@link FieldResolver#resolve(Object)} 便捷重载自动传入。</p>
 *
 * <p>典型用法：</p>
 * <pre>{@code
 *   // 1. 计算逻辑只写一次（与 DTO 解耦，但可读实体）
 *   FieldCalculator<AddressParts, Order, String> addressCalculator =
 *           (parts, order) -> String.join(" ",
 *                   parts.province, parts.city, parts.district,
 *                   parts.detail != null ? parts.detail
 *                           : (order != null ? order.getAddressDetail() : ""));
 *
 *   // 2. 创建场景：CreateOrderCommand + (null 实体) → AddressParts → String
 *   FieldResolver<CreateOrderCommand, Order, String> createResolver =
 *           FieldResolvers.from(CreateOrderCommand.class, Order.class, addressCalculator,
 *                   (dto, order) -> new AddressParts(
 *                           dto.getProvince(), dto.getCity(), dto.getDistrict(), dto.getDetail()));
 *
 *   // 3. 修改场景：复用同一个 Calculator，extractor 从实体补 detail
 *   FieldResolver<ChangeAddressCommand, Order, String> updateResolver =
 *           FieldResolvers.from(ChangeAddressCommand.class, Order.class, addressCalculator,
 *                   (dto, order) -> new AddressParts(
 *                           dto.getProvince(), dto.getCity(), dto.getDistrict(),
 *                           order != null ? order.getAddressDetail() : null));
 * }</pre>
 *
 * @author Li XiaoJing
 * @since 2.2.0
 */
public final class FieldResolvers {

    private FieldResolvers() {}

    /**
     * 从 Command DTO + 实体 中提取数据并计算。
     *
     * <p>把 {@code extractor}（如何从 DTO / 实体 取值）与 {@code calculator}
     * （纯计算逻辑，可读取实体）桥接起来，生成一个类型安全的 {@link FieldResolver}。</p>
     *
     * @param dtoType    Command DTO 类型（仅用于类型推断，不参与运行时）
     * @param entityType 实体类型（仅用于类型推断，不参与运行时）
     * @param calculator 字段计算器（纯计算逻辑，可读取实体）
     * @param extractor  如何从 Command DTO + 实体 中提取 Calculator 需要的原始数据
     * @param <C>        Command DTO 类型
     * @param <E>        实体类型
     * @param <T>        提取出的原始数据类型
     * @param <R>        计算结果类型
     * @return 类型安全的 FieldResolver
     */
    public static <C, E, T, R> FieldResolver<C, E, R> from(
            Class<C> dtoType,
            Class<E> entityType,
            FieldCalculator<T, E, R> calculator,
            BiFunction<C, E, T> extractor) {
        return (command, entity) -> calculator.calculate(extractor.apply(command, entity), entity);
    }
}
