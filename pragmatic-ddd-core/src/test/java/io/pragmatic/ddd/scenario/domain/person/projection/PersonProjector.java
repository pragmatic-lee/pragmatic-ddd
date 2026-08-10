package io.pragmatic.ddd.scenario.domain.person.projection;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.query.IAggregateProjector;
import io.pragmatic.ddd.scenario.domain.person.model.Person;

/**
 * 人员聚合投影器：将写模型聚合根映射为读模型投影（纯映射，不含存储细节）。
 *
 * @author wizard-lee
 */
public class PersonProjector implements IAggregateProjector<Person, PersonProjection> {

    @Override
    public PersonProjection project(Person aggregateRoot) {
        if (aggregateRoot == null) {
            return null;
        }
        return new PersonDetailProjection(
                aggregateRoot.getEntityId(),
                aggregateRoot.getOldVersion(),
                aggregateRoot.getName(),
                aggregateRoot.getGender() == null ? null : aggregateRoot.getGender().name(),
                aggregateRoot.getAge(),
                aggregateRoot.getIdCard(),
                aggregateRoot.getEmail(),
                aggregateRoot.getPhone(),
                aggregateRoot.getAvatarUrl(),
                aggregateRoot.getDepartmentId() == null ? null : String.valueOf(aggregateRoot.getDepartmentId()),
                aggregateRoot.getPosition(),
                aggregateRoot.getEmployeeNo(),
                aggregateRoot.getStatus() == null ? null : aggregateRoot.getStatus().name(),
                aggregateRoot.getTags(),
                aggregateRoot.getLevel(),
                aggregateRoot.getAddress());
    }

    @Override
    public Class<PersonProjection> projectionType() {
        return PersonProjection.class;
    }
}
