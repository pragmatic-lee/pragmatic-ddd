package io.pragmatic.ddd.mybatis.bootstrap;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * 框架 MyBatis 模块的可配置项（零 Spring 依赖）。
 *
 * <p>用于把「契约接口 + XML 实现」的装配与表名 / 模块开关 / 多库 XML 解耦，
 * 避免本框架被引入引用方项目时与其既有 MyBatis 用法冲突：
 * <ul>
 *   <li><b>表名可配置</b>：{@link #idSegmentTable()} / {@link #outboxTable()} 作为
 *       {@code ${idSegmentTable}} / {@code ${outboxTable}} 注入 mapper XML 与 schema，
 *       引用方已有同名业务表时改此即可，无需改 XML。</li>
 *   <li><b>模块可关闭</b>：{@link #idEnabled()} / {@link #outboxEnabled()} 决定是否装配对应契约，
 *       引用方只想用 type handler 或只需其中一个模块时可关闭另一个。</li>
 *   <li><b>多库 XML 切换</b>：{@link #idSegmentXml()} / {@link #outboxXml()} 指向 classpath 上的
 *       SQL 实现 XML；换数据库只需提供一份 namespace 相同的 XML 并改此路径，
 *       使用方 Java 代码零改动。</li>
 * </ul>
 *
 * <p>全部字段均有合理默认值（启用 id + outbox、内置 MySQL 表名与 XML），
 * 使用方按需覆盖；{@link #variables()} 汇总供 {@code ${...}} 替换的属性。</p>
 */
public final class MybatisModuleOptions {

    /**
     * 合法 SQL 标识符（可带一级 schema 限定，如 {@code myschema.my_table}）。
     * 表名占位符 {@code ${...}} 在 XML <b>解析期</b>被一次性替换为字面量（非运行期拼接），
     * 且值只能来自本配置类；此白名单校验进一步杜绝把不可信输入用作表名的可能，
     * 使 {@code ${}} 占位符不构成 SQL 注入通道。
     */
    private static final Pattern SQL_IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(\\.[A-Za-z_][A-Za-z0-9_$]*)?");

    /** 是否启用 ID 号段契约（IdSegmentMapper）。默认 true。 */
    private boolean idEnabled = true;
    /** 是否启用 Outbox 契约（OutboxMapper）。默认 true。 */
    private boolean outboxEnabled = true;

    /** id_segment 表名，供 mapper XML / schema 的 {@code ${idSegmentTable}} 替换。默认 "id_segment"。 */
    private String idSegmentTable = "id_segment";
    /** outbox_message 表名，供 {@code ${outboxTable}} 替换。默认 "outbox_message"。 */
    private String outboxTable = "outbox_message";

    /**
     * IdSegmentMapper 的 SQL 实现 XML（classpath 资源路径）。
     * 该 XML 的 namespace 必须同为 {@code io.pragmatic.ddd.mybatis.id.IdSegmentMapper}；
     * 换数据库只需替换此路径指向的 XML（如 postgres 实现），契约接口与其使用方均无需改动。
     */
    private String idSegmentXml = "io/pragmatic/ddd/mybatis/id/IdSegmentMapper.xml";

    /**
     * OutboxMapper 的 SQL 实现 XML（classpath 资源路径）。
     * namespace 必须同为 {@code io.pragmatic.ddd.mybatis.outbox.OutboxMapper}。
     */
    private String outboxXml = "io/pragmatic/ddd/mybatis/outbox/OutboxMapper.xml";

    /** 可选：type handler 上下文；非 null 时由 {@link MybatisModuleBootstrap} 在 build 后统一注册。 */
    private TypeHandlerContext typeHandlerContext;

    private MybatisModuleOptions() {
    }

    /** 返回全部字段取默认值的实例。 */
    public static MybatisModuleOptions defaults() {
        return new MybatisModuleOptions();
    }

    // ---- fluent 配置入口（返回 this，支持链式）----

    public MybatisModuleOptions idEnabled(boolean v) {
        this.idEnabled = v;
        return this;
    }

    public MybatisModuleOptions outboxEnabled(boolean v) {
        this.outboxEnabled = v;
        return this;
    }

    /** @throws IllegalArgumentException 表名不是合法 SQL 标识符时（防注入白名单校验） */
    public MybatisModuleOptions idSegmentTable(String v) {
        this.idSegmentTable = requireSqlIdentifier(v, "idSegmentTable");
        return this;
    }

    /** @throws IllegalArgumentException 表名不是合法 SQL 标识符时（防注入白名单校验） */
    public MybatisModuleOptions outboxTable(String v) {
        this.outboxTable = requireSqlIdentifier(v, "outboxTable");
        return this;
    }

    private static String requireSqlIdentifier(String v, String field) {
        if (v == null || !SQL_IDENTIFIER.matcher(v).matches()) {
            throw new IllegalArgumentException(
                    field + " must be a plain (optionally schema-qualified) SQL identifier, got: " + v);
        }
        return v;
    }

    public MybatisModuleOptions idSegmentXml(String v) {
        this.idSegmentXml = v;
        return this;
    }

    public MybatisModuleOptions outboxXml(String v) {
        this.outboxXml = v;
        return this;
    }

    public MybatisModuleOptions typeHandlerContext(TypeHandlerContext v) {
        this.typeHandlerContext = v;
        return this;
    }

    // ---- 取值 ----

    public boolean isIdEnabled() {
        return idEnabled;
    }

    public boolean isOutboxEnabled() {
        return outboxEnabled;
    }

    public String idSegmentTable() {
        return idSegmentTable;
    }

    public String outboxTable() {
        return outboxTable;
    }

    public String idSegmentXml() {
        return idSegmentXml;
    }

    public String outboxXml() {
        return outboxXml;
    }

    public TypeHandlerContext typeHandlerContext() {
        return typeHandlerContext;
    }

    /**
     * 供 mapper XML / schema 做 {@code ${...}} 变量替换的属性集合。
     * 键名与 XML、schema 中的占位符严格对应。
     */
    public Properties variables() {
        Properties p = new Properties();
        p.put("idSegmentTable", idSegmentTable);
        p.put("outboxTable", outboxTable);
        return p;
    }
}
