package io.pragmatic.ddd.mybatis.bootstrap;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 框架 MyBatis 模块的零冲突装配器（Spring 无关）。
 *
 * <p>把「契约接口 + XML 实现」的装配从引用方既有用法中隔离出来，落地下述两点：
 * <ul>
 *   <li><b>模块可关闭</b>：按 {@link MybatisModuleOptions} 的开关，仅加载启用模块的 XML。</li>
 *   <li><b>多库 XML 切换</b>：用 {@link XMLMapperBuilder} <b>显式</b>加载由 options 指定的 XML，
 *       而非依赖「接口名.xml」的隐式自动发现。XML 的 namespace 即契约接口，
 *       加载后由 MyBatis 的 {@code bindMapperForNamespace} 自动注册该接口并绑定语句，
 *       因而<b>无需再 {@code addMapper} 对应接口</b>；多份 namespace 相同的 DB XML 可共存于
 *       classpath，仅加载其中一份即完成数据库切换。</li>
 * </ul>
 *
 * <p>表名默认直接写死在内置 XML（{@code id_segment} / {@code outbox_message}）中，
 * 默认用法零配置；要换表名 / 换数据库，由引用方提供同 namespace 的 XML 并通过
 * {@link MybatisModuleOptions#idSegmentXml()} / {@link MybatisModuleOptions#outboxXml()} 指定。</p>
 *
 * <p>使用方式分两种：
 * <ol>
 *   <li>自定义 Environment / 事务工厂的引用方：分步调用
 *       {@link #configure(Configuration)}（build 前）与
 *       {@link #registerTypeHandlers(SqlSessionFactory)}（build 后）。</li>
 *   <li>希望一站式构建的引用方：直接调用 {@link #build(DataSource)} /
 *       {@link #build(Environment)}。</li>
 * </ol>
 */
public final class MybatisModuleBootstrap {

    private final MybatisModuleOptions options;

    public MybatisModuleBootstrap(MybatisModuleOptions options) {
        this.options = options == null ? MybatisModuleOptions.defaults() : options;
    }





    /**
     * 一站式构建（使用默认 {@link JdbcTransactionFactory} 与给定数据源）。
     * 等价于 {@code configure} + {@code build} + {@link #registerTypeHandlers(SqlSessionFactory)}。
     */
    public SqlSessionFactory build(DataSource dataSource) {
        return build(new Environment("pragmatic-ddd", new JdbcTransactionFactory(), dataSource));
    }
    /** 一站式构建（使用调用方自定义的 {@link Environment}）。 */
    public SqlSessionFactory build(Environment environment) {
        Configuration cfg = new Configuration(environment);
        SqlSessionFactory ssf = new SqlSessionFactoryBuilder().build(cfg);
        return ssf;
    }
}
