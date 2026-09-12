package io.pragmatic.ddd.scenario.application.person;

import io.pragmatic.ddd.application.IQueryApplicationService;
import io.pragmatic.ddd.repository.query.exception.ProjectionReducerNotFoundException;
import io.pragmatic.ddd.repository.query.projection.IReducer;
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

    private final IPersonQuery source;

    public PersonReadService(IPersonQuery source) {
        this.source = source;
    }

    public PersonDetailProjection getPerson(long id) {
        PersonProjection full = source.getById(id);
        if (full == null) {
            return null;
        }
        return reduceWith(source.getReducer(PersonDetailProjection.class), full, PersonDetailProjection.class);
    }

    public List<PersonProjection> listPersons(List<Long> ids) {
        return source.getByIds(List.copyOf(ids));
    }

    private <X extends PersonProjection> X reduceWith(
            IReducer<PersonProjection, X> reducer, PersonProjection full, Class<X> target) {
        if (target.isInstance(full)) {
            return target.cast(full);
        }
        if (reducer == null) {
            throw new ProjectionReducerNotFoundException("未注册裁剪器: " + target.getName());
        }
        return reducer.reduce(full);
    }
}
