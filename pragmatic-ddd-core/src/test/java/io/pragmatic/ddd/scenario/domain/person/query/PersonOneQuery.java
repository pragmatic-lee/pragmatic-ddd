package io.pragmatic.ddd.scenario.domain.person.query;

import io.pragmatic.ddd.repository.query.OneQueryCriteria;

import java.util.List;

/**
 * 人员单投影查询（queryOne）的条件族。
 *
 * <p>继承框架分族父类 {@link OneQueryCriteria}，族内以 sealed interface + record permits
 * 横向扩展具体场景；新增场景只需新增 record 并在 permits 登记，穷举分支由编译器担保。</p>
 *
 * @author wizard-lee
 */
public sealed interface PersonOneQuery extends OneQueryCriteria
        permits PersonOneQuery.LatestByDepartment {
    /**
     * 按部门取最新创建的人员。
     *
     * @param departmentId 部门 ID（必填、精确匹配）
     */
    record LatestByDepartment(Long departmentId) implements PersonOneQuery {
    }

    static List<PersonOneQuery> all() {
        return List.of(new LatestByDepartment(null));
    }
}
