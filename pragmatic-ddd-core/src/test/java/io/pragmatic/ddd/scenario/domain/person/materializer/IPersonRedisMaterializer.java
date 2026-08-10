package io.pragmatic.ddd.scenario.domain.person.materializer;

import io.pragmatic.ddd.repository.query.IProjectionMaterializer;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonProjection;

/**
 * Redis 物化契约（领域层仅接口，实现由基础设施层提供）。
 *
 * @author wizard-lee
 */
public interface IPersonRedisMaterializer extends IProjectionMaterializer<PersonProjection> {
}
