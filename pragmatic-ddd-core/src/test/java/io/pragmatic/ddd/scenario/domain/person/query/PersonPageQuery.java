package io.pragmatic.ddd.scenario.domain.person.query;

import io.pragmatic.ddd.repository.query.PageQueryCriteria;

import java.util.List;
import java.util.Optional;

/**
 * 人员分页 / 滚动查询（queryPage / queryScroll）共用的条件族。
 *
 * <p>继承框架分族父类 {@link PageQueryCriteria}，族内以 sealed interface + record permits
 * 横向扩展具体场景；本族字段全 Optional（按需过滤），与 One / List 族的精确必填语义区分。</p>
 *
 * @author wizard-lee
 */
public sealed interface PersonPageQuery extends PageQueryCriteria
        permits PersonPageQuery.ByDepartment {
    /**
     * 按部门过滤（可选）。
     *
     * @param departmentId 部门 ID（Optional，不传则不参与筛选）
     */
    record ByDepartment(Optional<Long> departmentId) implements PersonPageQuery {
    }

    static List<PersonPageQuery> all() {
        return List.of(new ByDepartment(Optional.empty()));
    }
}
