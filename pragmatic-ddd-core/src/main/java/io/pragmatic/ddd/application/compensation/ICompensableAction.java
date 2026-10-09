package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

import java.util.Optional;

/**
 * 可补偿动作契约：一个"正向调用外部系统 + 逆向回滚外部系统"的配对单元。
 * 绑定所属聚合类型与需求类型——正向、回写、补偿一律从聚合读取业务事实，而不是从入参读取。
 * 与事件订阅者同构：泛型绑定具体需求类型，收到即强类型。
 *
 * <p>实现必须无状态（不得持有本次调用的中间结果），以便作为单例由容器管理并注入外部客户端。</p>
 *
 * @param <A>   所属聚合根类型
 * @param <REQ> 需求类型（IExternalRequirement 的具体 record 实现）
 * @param <T>   正向产出类型
 * @author wizard-lee
 */
public interface ICompensableAction<A extends AggregateRoot<?>, REQ extends IExternalRequirement, T> {

    /** 本动作负责的需求类型，供解析器按 Class 路由与校验。 */
    Class<REQ> requirementType();

    /** 本动作适用的聚合类型，供解析器做类型校验。 */
    Class<A> aggregateType();

    /** 正向执行：调用外部系统完成写入 / 预占 / 锁定，返回供本地聚合使用的产出。 */
    T execute(A aggregateRoot, REQ requirement);

    /** 回写聚合：只能调用聚合的业务方法（含状态机守卫），禁止 setter 直改。 */
    void apply(A aggregateRoot, T result);

    /** 逆向补偿：调用外部系统回滚接口，必须幂等。 */
    void compensate(A aggregateRoot, T result);

    /**
     * 持久化载荷：正向执行之前即可确定的补偿依据（聚合标识、SKU、数量等），
     * 供 L3 崩溃后由中继还原补偿。补偿不得依赖外部返回值——外部系统须支持按业务单号幂等撤销。
     * 默认返回空串，表示该动作不支持 L3 兜底；开启 durable 时显式失败，不静默降级。
     */
    default String payload(A aggregateRoot, REQ requirement) {
        return "";
    }

    /** 补偿重试策略；返回 empty 表示采用补偿范围配置的默认策略。 */
    default Optional<CompensationPolicy> policy() {
        return Optional.empty();
    }
}
