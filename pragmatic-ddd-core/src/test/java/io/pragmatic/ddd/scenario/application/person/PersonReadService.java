package io.pragmatic.ddd.scenario.application.person;

import io.pragmatic.ddd.application.IQueryApplicationService;
import io.pragmatic.ddd.scenario.domain.person.projection.IPersonQuery;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonDetailProjection;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonProjection;

import java.util.List;

/**
 * 人员查询应用服务（CQRS 读侧，绕过聚合根直查投影）。
 *
 * @author wizard-lee
 */
public class PersonReadService implements IQueryApplicationService {

    private final IPersonQuery query;

    public PersonReadService(IPersonQuery query) {
        this.query = query;
    }

    public PersonDetailProjection getPerson(long id) {
        return (PersonDetailProjection) query.queryById(id);
    }

    public List<PersonProjection> listPersons(List<Long> ids) {
        return query.queryByIds(ids);
    }
}
