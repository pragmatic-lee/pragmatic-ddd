package io.pragmatic.ddd.scenario.infrastructure.person.materializer;

import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import io.pragmatic.ddd.scenario.domain.person.materializer.IPersonEsMaterializer;
import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonProjection;

/**
 * ES 物化器（壳子）。
 * 示例框架不连真实 ES，方法体为空实现；真实项目在此写/删 ES 副本并登记到 Registry。
 * 仅实现 IProjectionMaterializer 链（IPersonEsMaterializer），由 target() 表达对账目标；
 * 补同步 resync / supportedTarget 由框架对账引擎经 Registry 驱动，壳子阶段不强制实现。
 *
 * @author wizard-lee
 */
public class PersonEsMaterializer implements IPersonEsMaterializer {

    @Override
    public Class<PersonProjection> projectionType() {
        return PersonProjection.class;
    }

    @Override
    public ReconciliationTarget target() {
        return new ReconciliationTarget(Person.class, "es:person");
    }

    @Override
    public void materialize(PersonProjection projection, long version) {
        // 壳子：真实项目在此写/更新 ES 副本
    }

    @Override
    public void purge(Object aggregateId) {
        // 壳子：真实项目在此清理 ES 残留条目
    }
}
