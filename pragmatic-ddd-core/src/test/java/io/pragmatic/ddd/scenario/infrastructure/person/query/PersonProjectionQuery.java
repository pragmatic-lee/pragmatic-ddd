package io.pragmatic.ddd.scenario.infrastructure.person.query;

import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import io.pragmatic.ddd.scenario.domain.person.projection.IPersonQuery;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonProjection;
import io.pragmatic.ddd.scenario.domain.person.query.PersonListQuery;
import io.pragmatic.ddd.scenario.domain.person.query.PersonOneQuery;
import io.pragmatic.ddd.scenario.domain.person.query.PersonPageQuery;

import java.util.List;

/**
 * 人员投影查询实现（壳子）。
 * 示例框架不连真实异构存储，方法体返回空值占位；真实项目直查 ES / Redis 绕过聚合根。
 *
 * @author wizard-lee
 */
public class PersonProjectionQuery implements IPersonQuery {

    @Override
    public <X extends PersonProjection> X queryById(Long id, Class<X> projectionType) {
        // 壳子：真实项目直查异构存储
        return null;
    }

    @Override
    public <X extends PersonProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
        // 壳子：真实项目直查异构存储
        return List.of();
    }

    @Override
    public <X extends PersonProjection> X queryOne(PersonOneQuery query, Class<X> projectionType) {
        // 壳子：真实项目直查异构存储
        return null;
    }

    @Override
    public <X extends PersonProjection> List<X> queryList(PersonListQuery query, Class<X> projectionType) {
        // 壳子：真实项目直查异构存储
        return List.of();
    }

    @Override
    public <X extends PersonProjection> PageResult<X> queryPage(
            PersonPageQuery query, PageRequest pageRequest, Class<X> projectionType) {
        // 壳子：真实项目直查异构存储
        return PageResult.of(List.of(), 0L, pageRequest);
    }

    @Override
    public <X extends PersonProjection> ScrollResult<X> queryScroll(
            PersonPageQuery query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
        // 壳子：真实项目直查异构存储
        return ScrollResult.of(List.of(), null);
    }
}
