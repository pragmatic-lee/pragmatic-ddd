package io.pragmatic.ddd.mybatis.bootstrap;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

/**
 * 框架 MyBatis 模块的零冲突装配器（Spring 无关）。
 *
 * <p>把「契约接口 + XML 实现」的装配从引用方既有用法中隔离出来，落地下述三点：
 * <ul>
 *   <li><b>表名可配置</b>：{@link #configure(Configuration)} 把
 *       {@code ${idSegmentTable}} / {@code ${outboxTable}} 注入 Configuration 变量，
 *       mapper XML 与 schema 中的占位符在解析时即被替换为真实表名。</li>
 *   <li><b>模块可关闭</b>：按 {@link MybatisModuleOptions} 的开关，仅加载启用模块的 XML。</li>
 *   <li><b>多库 XML 切换</b>：用 {@link XMLMapperBuilder} <b>显式</b>加载由 options 指定的 XML，
 *       而非依赖「接口名.xml」的隐式自动发现。XML 的 namespace 即契约接口，
 *       加载后由 MyBatis 的 {@code bindMapperForNamespace} 自动注册该接口并绑定语句，
 *       因而<b>无需再 {@code addMapper} 对应接口</b>；多份 namespace 相同的 DB XML 可共存于
 *       classpath，仅加载其中一份即完成数据库切换。</li>
 * </ul>
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
     * 在 {@code new SqlSessionFactoryBuilder().build(cfg)} <b>之前</b>调用：
     * 注入表名变量 + 按开关用 {@link XMLMapperBuilder} 显式加载各契约接口的 SQL 实现 XML。
     *
     * <p>注意：XML 被加载后 MyBatis 会把 namespace 对应的契约接口自动注册进 MapperRegistry，
     * 因此调用方<b>不要再</b> {@code addMapper} 这些契约接口，否则会触发
     * "Mapped Statements collection already contains value for ..."。</p>
     */
    public void configure(Configuration configuration) {
        Properties vars = configuration.getVariables();
        if (vars == null) {
            vars = new Properties();
        }
        vars.putAll(options.variables());
        configuration.setVariables(vars);

        if (options.isIdEnabled()) {
            loadMapperXml(configuration, options.idSegmentXml());
        }
        if (options.isOutboxEnabled()) {
            loadMapperXml(configuration, options.outboxXml());
        }
    }

    /**
     * 在 {@link SqlSessionFactory} 构建<b>之后</b>调用：若配置了
     * {@link TypeHandlerContext} 则统一注册枚举 / JSON / 集合三通道 type handler。
     */
    public void registerTypeHandlers(SqlSessionFactory sqlSessionFactory) {
        TypeHandlerContext ctx = options.typeHandlerContext();
        if (ctx != null) {
            ctx.registerInto(sqlSessionFactory);
        }
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
        configure(cfg);
        SqlSessionFactory ssf = new SqlSessionFactoryBuilder().build(cfg);
        registerTypeHandlers(ssf);
        return ssf;
    }

    /**
     * 执行 schema 脚本，并对 {@code ${idSegmentTable}} / {@code ${outboxTable}} 做变量替换，
     * 使建表语句与 mapper XML 的表名保持一致、端到端可配置。
     *
     * <p>使用 {@link PropertyParser} 完成 {@code ${...}} 替换（与 MyBatis XML 解析同一套机制），
     * 然后交由 {@link ScriptRunner} 执行。</p>
     *
     * @param connection         已建立的连接（方法不负责关闭）
     * @param schemaResourcePath classpath 上的 schema SQL 资源路径（前导 “/” 会被自动去除，
     *                           因为 MyBatis {@link Resources} 走 ClassLoader 加载，不接受前导斜杠）
     */
    public void runSchema(Connection connection, String schemaResourcePath) throws IOException {
        String path = schemaResourcePath.startsWith("/")
                ? schemaResourcePath.substring(1)
                : schemaResourcePath;
        try (InputStream in = Resources.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("schema not found on classpath: " + path);
            }
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            sql = PropertyParser.parse(sql, options.variables());
            ScriptRunner runner = new ScriptRunner(connection);
            try (StringReader reader = new StringReader(sql)) {
                runner.runScript(reader);
            }
        }
    }

    private void loadMapperXml(Configuration configuration, String resource) {
        try (InputStream in = Resources.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("mapper xml not found on classpath: " + resource);
            }
            Map<String, XNode> sqlFragments = configuration.getSqlFragments();
            new XMLMapperBuilder(in, configuration, resource, sqlFragments).parse();
        } catch (IOException e) {
            throw new IllegalStateException("failed to load mapper xml: " + resource, e);
        }
    }
}
