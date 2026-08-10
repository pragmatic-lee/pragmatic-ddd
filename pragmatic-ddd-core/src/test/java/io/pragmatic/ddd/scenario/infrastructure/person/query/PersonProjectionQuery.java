package io.pragmatic.ddd.scenario.infrastructure.person.query;

import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import io.pragmatic.ddd.scenario.domain.person.projection.IPersonQuery;
import io.pragmatic.ddd.scenario.domain.person.projection.PersonProjection;

import java.util.List;

/**
 * 人员投影查询实现（壳子）。
 * 示例框架不连真实异构存储，方法体返回空值占位；真实项目直查 ES / Redis 绕过聚合根。
 *
 * @author wizard-lee
 */
public class PersonProjectionQuery implements IPersonQuery {

    @Override
    public PersonProjection queryById(Long id) {
        // 壳子：真实项目直查异构存储
        return null;
    }

    @Override
    public List<PersonProjection> queryByIds(List<Long> ids) {
        // 壳子：真实项目直查异构存储
        return List.of();
    }

    @Override
    public PersonProjection queryOne(Object query) {
        // 壳子：真实项目直查异构存储
        return null;
    }

    @Override
    public List<PersonProjection> queryList(Object query) {
        // 壳子：真实项目直查异构存储
        return List.of();
    }

    @Override
    public PageResult<PersonProjection> queryPage(Object query, PageRequest pageRequest) {
        // 壳子：真实项目直查异构存储
        return PageResult.of(List.of(), 0L, pageRequest);
    }

    @Override
    public ScrollResult<PersonProjection> queryScroll(Object query, ScrollPosition cursor, int pageSize) {
        // 壳子：真实项目直查异构存储
        return ScrollResult.of(List.of(), null);
    }
}
