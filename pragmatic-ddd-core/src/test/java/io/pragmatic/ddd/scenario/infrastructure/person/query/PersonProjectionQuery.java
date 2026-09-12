package io.pragmatic.ddd.scenario.infrastructure.person.query;

import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import io.pragmatic.ddd.scenario.domain.person.projection.IPersonQuery;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonProjection;
import io.pragmatic.ddd.scenario.domain.person.query.PersonListQuery;
import io.pragmatic.ddd.scenario.domain.person.query.PersonOneQuery;
import io.pragmatic.ddd.scenario.domain.person.query.PersonPageQuery;

import java.util.List;

/**
 * 人员投影源实现（壳子）。
 * 示例框架不连真实异构存储，方法体返回空值占位；真实项目直查 ES / Redis 绕过聚合根。
 *
 * @author wizard-lee
 */
public class PersonProjectionQuery implements IPersonQuery {

    @Override
    public PersonProjection getById(Object id) {
        // 壳子：真实项目直查异构存储
        return null;
    }

    @Override
    public List<PersonProjection> getByIds(List<Object> ids) {
        // 壳子：真实项目直查异构存储
        return List.of();
    }

    @Override
    public List<PersonProjection> search(PersonOneQuery criteria) {
        // 壳子：真实项目直查异构存储
        return List.of();
    }

    @Override
    public List<PersonProjection> search(PersonListQuery criteria) {
        // 壳子：真实项目直查异构存储
        return List.of();
    }

    @Override
    public PageResult<PersonProjection> searchPage(PersonPageQuery criteria, PageRequest pageRequest) {
        // 壳子：真实项目直查异构存储
        return PageResult.of(List.of(), 0L, pageRequest);
    }

    @Override
    public ScrollResult<PersonProjection> searchScroll(
            PersonPageQuery criteria, ScrollPosition cursor, int pageSize) {
        // 壳子：真实项目直查异构存储
        return ScrollResult.of(List.of(), null);
    }

    @Override
    public <X extends IAggregateProjection> IReducer<PersonProjection, X> getReducer(Class<X> target) {
        // 壳子：真实项目按目标子投影类型返回裁剪器
        return null;
    }
}
