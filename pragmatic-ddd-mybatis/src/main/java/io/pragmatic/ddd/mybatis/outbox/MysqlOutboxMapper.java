package io.pragmatic.ddd.mybatis.outbox;

/**
 * Outbox Mapper 的 <b>MySQL 实现</b>（本模块唯一内置实现）。
 *
 * <p>继承通用 {@link OutboxMapper} 契约，所有方法的具体 SQL 固化于
 * {@code MysqlOutboxMapper.xml}（MySQL 语法，如 {@code LIMIT #{batchSize}}）。MyBatis 运行期
 * 为其生成动态代理（{@code MapperProxy}）并绑定上述 XML——本接口<b>不标注 {@code @Mapper}</b>，
 * 与模块内 type handler 的手动注册风格一致：使用方在构建 {@code SqlSessionFactory} 后调用
 * {@code configuration.addMapper(MysqlOutboxMapper.class)} 注册（同级 XML 自动加载并绑定）。</p>
 *
 * <p>如需 PostgreSQL / H2 等，使用方自行定义 {@code XxxOutboxMapper extends OutboxMapper}
 * 并附带对应 XML、同样手动 {@code addMapper} 注册即可，无需改动本模块。</p>
 *
 * @author Li XiaoJing
 * @since 2.5.0
 */
public interface MysqlOutboxMapper extends OutboxMapper {
}
