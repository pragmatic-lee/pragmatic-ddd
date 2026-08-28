package io.pragmatic.ddd.scenario.domain.person.query;

import io.pragmatic.ddd.repository.query.ListQueryCriteria;

import java.util.List;

/**
 * 人员列表查询（queryList）的条件族。
 *
 * <p>继承框架分族父类 {@link ListQueryCriteria}，族内以 sealed interface + record permits
 * 横向扩展具体场景；新增场景只需新增 record 并在 permits 登记，穷举分支由编译器担保。</p>
 *
 * @author wizard-lee
 */
public sealed interface PersonListQuery extends ListQueryCriteria
        permits PersonListQuery.ListByDepartment,
                PersonListQuery.ListByStatus,
                PersonListQuery.ListByDepartmentAndStatus {
    /**
     * 按部门查询人员列表。
     *
     * @param departmentId 部门 ID（必填、精确匹配）
     */
    record ListByDepartment(Long departmentId) implements PersonListQuery {
    }

    /**
     * 按状态查询人员列表。
     *
     * @param status 人员状态（必填、精确匹配）
     */
    record ListByStatus(Integer status) implements PersonListQuery {
    }

    /**
     * 按部门与状态联合查询人员列表。
     *
     * @param departmentId 部门 ID（必填、精确匹配）
     * @param status       人员状态（必填、精确匹配）
     */
    record ListByDepartmentAndStatus(Long departmentId, Integer status) implements PersonListQuery {
    }

    static List<PersonListQuery> all() {
        return List.of(
                new ListByDepartment(null),
                new ListByStatus(null),
                new ListByDepartmentAndStatus(null, null));
    }
}
