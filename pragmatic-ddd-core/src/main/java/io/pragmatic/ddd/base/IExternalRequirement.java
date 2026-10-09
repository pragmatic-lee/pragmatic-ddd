package io.pragmatic.ddd.base;

/**
 * 外部需求契约：聚合声明"需要外部世界配合完成的一件事"。
 * 只描述要什么，不描述怎么做——怎么做由应用层的可补偿动作负责。
 * 实现为不可变 record，字段显式声明；无参数需求的实现为空 record（参数在聚合上，Action 直接读聚合）。
 *
 * @author wizard-lee
 */
public interface IExternalRequirement {

    /**
     * 需求编码：L3 持久化路由键（compensation_log.handler 列），
     * 跨进程、跨版本稳定，不得由类名推导。
     *
     * @return 需求编码
     */
    String code();
}
