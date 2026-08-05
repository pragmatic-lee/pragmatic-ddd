/**
 * 仓储契约包。
 *
 * <p>命令侧（写模型）：{@link IRepository} 定义聚合持久化契约（insert / update / save / findById / remove），
 * {@link AbstractRepository} 提供抽象基类，在落库前统一触发聚合根数据同步钩子（triggerDataSyncHook）。</p>
 *
 * <p>查询侧（读模型）：见 {@link io.pragmatic.ddd.repository.query} 子包 —— 聚合级查询（Q 侧）契约与读模型投影。</p>
 *
 * <p>读模型对账：见 {@link io.pragmatic.ddd.repository.reconciliation} 子包 —— 读模型补偿、去重与版本对账。</p>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository;
