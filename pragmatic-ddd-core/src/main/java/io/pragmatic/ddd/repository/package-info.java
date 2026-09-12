/**
 * 仓储契约包。
 *
 * <p>命令侧（写模型）：{@link IRepository} 定义聚合持久化契约（insert / update / save / findById / remove），
 * {@link AbstractRepository} 提供抽象基类，在落库前统一触发聚合根数据同步钩子（triggerDataSyncHook）。</p>
 *
 * <p>查询侧（读模型）：见 {@link io.pragmatic.ddd.repository.query} 子包 —— 聚合级查询（Q 侧）契约与读模型投影。</p>
 *
 * <p>读模型副本：{@link IReadModelReplica} 定义一份物理副本（ES 一个索引 / Redis 一个键空间）的
 * 自我维护契约（副本身份 + 版本读取 + 自我重建 + 残留清理），{@link ReplicaKey} 为其寻址键。
 * 读侧源（{@code AbstractProjectionSource}）实现该契约后「源即副本」，读写与对账收敛于同一对象。</p>
 *
 * <p>读模型对账：见 {@link io.pragmatic.ddd.repository.reconciliation} 子包 —— 读模型补偿、去重与版本对账。
 * 该子包只依赖 {@link IReadModelReplica}，不认识读侧投影类型。</p>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository;
