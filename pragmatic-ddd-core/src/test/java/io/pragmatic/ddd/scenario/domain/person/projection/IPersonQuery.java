package io.pragmatic.ddd.scenario.domain.person.projection;

import io.pragmatic.ddd.repository.query.IAggregateQuery;

/**
 * 人员读模型查询能力组合（CQRS 读侧，绕过聚合根）。
 *
 * @author wizard-lee
 */
public interface IPersonQuery extends IAggregateQuery<Long, PersonProjection, Object, Object, Object> {
}
