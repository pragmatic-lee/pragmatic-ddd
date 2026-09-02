package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 按条件投影检索器：从某异构存储按业务条件取回投影列表（含单条取首条）。
 * 与 {@link AbstractProjectionSource} 的 {@code materialize} 对称——materialize 负责"投影 → 存储"，
 * searcher 负责"存储 → 投影"。由各集成模块（ES / Redis / 读表连接器）实现，
 * core 只定义中立接口，不依赖任何存储客户端，也不定义中性查询规约。
 *
 * <p>检索的"条件"直接以业务侧条件子类（继承分族父类的 sealed 子类型）传入，
 * 由实现类自行决定如何翻译为各自存储的检索请求；框架不插入任何中间载体。
 * 一个 searcher 实例服务一个聚合族的某类条件 + 某索引级全量投影类型，由
 * {@link ProjectorRegistry} 按 {@code (criteriaType, projectionType)} 二维键定位。</p>
 *
 * <p>其服务的投影类型是<b>索引级全量投影类型</b>——即对齐某个物理存储索引
 * 文档形状的具体投影类（如 {@code OrderEsProjection.class}），而非业务子投影或投影体系接口。
 * 检索器只负责取回该全量形状；若调用方要的是业务子投影，由
 * {@link IProjectionReducer} 在 Java 内存中二次裁剪，检索器不参与。</p>
 *
 * <p>按主键 / 批量主键直取的场景不属于本接口，由 {@link IProjectionByIdSearcher} 覆盖；
 * 分页 / 滚动场景由 {@link IProjectionPagedSearcher} 覆盖。</p>
 *
 * @param <C> 业务条件类型（继承 {@link QueryCriteria}，如 OneQueryCriteria / ListQueryCriteria）
 * @param <P> 投影类型
 * @author wizard-lee
 */
public interface IProjectionSearcher<C extends QueryCriteria, P extends IAggregateProjection> {

    /** 本检索器服务的业务条件类型，供按型定位。 */
    Class<C> criteriaType();

    /**
     * 按业务条件取回投影列表（无结果返回空列表，不返回 null）。
     * 检索器只服务所属源，投影类型由源持有，故此处无需再传投影类型。
     */
    List<P> search(C condition);
}
