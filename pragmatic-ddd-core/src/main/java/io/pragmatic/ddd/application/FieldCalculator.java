package io.pragmatic.ddd.application;

/**
 * 字段计算器 —— 从原始数据 + 实体 中完成字段计算。
 *
 * <p>这是 v3 方案里开发者真正需要实现的接口。它<b>不关心</b>入参是
 * {@code CreateOrderCommand} 还是 {@code ChangeAddressCommand}，只关心
 * "给我省份、城市、区县，我算出完整地址" 这类纯粹的计算。</p>
 *
 * <p>v3 增强：计算逻辑除了接收从 Command DTO 提取的原始数据 {@code source}，
 * 还会收到<b>实体 {@code entity}</b>。这是因为"字段计算"本质上往往是在计算实体上的字段，
 * 尤其是修改场景：新值常常要参考实体当前状态（保留命令未改的字段、基于旧值算差值等）。</p>
 *
 * <p>create 场景中实体尚不存在，{@code entity} 为 {@code null}；
 * update 场景中 {@code entity} 为从仓储加载的已有实体（非 null）。
 * 计算逻辑应对 {@code entity == null} 做好兼容（纯命令驱动的计算可以安全忽略它）。</p>
 *
 * <p>{@code FieldCalculator} 是"计算逻辑的原子单元"：</p>
 * <ul>
 *   <li><b>与 DTO 解耦</b>：只接收计算所需的原始数据 {@code T}，不知道数据来自哪个命令；</li>
 *   <li><b>可读实体</b>：需要实体状态时直接读 {@code entity}，无需在 Updater 里手动搬运；</li>
 *   <li><b>天然可复用</b>：创建、修改等多个场景可以共用同一个 Calculator；</li>
 *   <li><b>易于单测</b>：无需构造任何命令对象，直接传入 {@code T}（与可选 {@code entity}）即可测试。</li>
 * </ul>
 *
 * <p>框架通过 {@link FieldResolvers#from(Class, Class, FieldCalculator, java.util.function.BiFunction)}
 * 把同一个 Calculator 适配到不同的 Command DTO / 实体组合，开发者只需写计算逻辑。</p>
 *
 * @param <T> 原始数据类型（由提取器从 Command DTO / 实体 中提取并传入）
 * @param <E> 实体类型（聚合根；创建场景为 null）
 * @param <R> 计算结果类型
 * @author Li XiaoJing
 * @since 2.2.0
 */
@FunctionalInterface
public interface FieldCalculator<T, E, R> {

    /**
     * 执行计算。
     *
     * @param source  已提取的原始数据（命令 + 实体的字段快照）
     * @param entity  实体实例；创建场景为 null
     * @return 计算结果
     */
    R calculate(T source, E entity);
}
