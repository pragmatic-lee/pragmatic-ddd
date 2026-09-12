package io.pragmatic.ddd.scenario.domain.person.projection;

import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IListQuerySearcher;
import io.pragmatic.ddd.repository.query.projection.IOneQuerySearcher;
import io.pragmatic.ddd.repository.query.projection.IPagedQuerySearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import io.pragmatic.ddd.scenario.domain.person.query.PersonListQuery;
import io.pragmatic.ddd.scenario.domain.person.query.PersonOneQuery;
import io.pragmatic.ddd.scenario.domain.person.query.PersonPageQuery;

/**
 * 人员投影源接口（领域层端口）：声明该副本支持的读能力。
 *
 * <p>由基础设施层实现，应用层只依赖本接口——不依赖具体存储实现。能力族决定
 * "能调哪些方法"：本接口声明四族全能力。</p>
 *
 * @author wizard-lee
 */
public interface IPersonQuery
        extends IProjectionByIdSearcher<PersonProjection>,
                IOneQuerySearcher<PersonProjection, PersonOneQuery>,
                IListQuerySearcher<PersonProjection, PersonListQuery>,
                IPagedQuerySearcher<PersonProjection, PersonPageQuery> {

    /**
     * 按目标子投影类型取裁剪器。
     *
     * @param target 目标子投影类型
     * @param <X> 目标子投影类型
     * @return 裁剪器；未注册返回 null
     */
    <X extends IAggregateProjection> IReducer<PersonProjection, X> getReducer(Class<X> target);
}
