package io.pragmatic.ddd.scenario.domain.person.projection;

import io.pragmatic.ddd.repository.query.IAggregateProjection;

/**
 * 人员聚合拓扑级投影（sealed 基类）。
 * 容纳概要/详情两类投影，供读模型查询与物化器按型定位。
 *
 * @author wizard-lee
 */
public sealed interface PersonProjection extends IAggregateProjection
        permits PersonSummaryProjection, PersonDetailProjection {

    /** 聚合 ID。 */
    Long id();

    /** 写模型快照版本，供对账 currentVersion 比对。 */
    long version();
}
