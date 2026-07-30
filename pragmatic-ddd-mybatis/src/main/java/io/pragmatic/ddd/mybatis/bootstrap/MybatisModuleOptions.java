package io.pragmatic.ddd.mybatis.bootstrap;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;

/**
 * 框架 MyBatis 模块的可配置项（零 Spring 依赖）。
 *
 * <p>用于把「契约接口 + XML 实现」的装配与模块开关 / 多库 XML 解耦，
 * 避免本框架被引入引用方项目时与其既有 MyBatis 用法冲突：
 * <ul>
 *   <li><b>默认表名写死于 XML</b>：内置 {@code IdSegmentMapper.xml} / {@code OutboxMapper.xml}
 *       直接使用字面量 {@code id_segment} / {@code outbox_message}，默认用法零配置，
 *       引用方无需关心任何表名变量。要换表名 / 换数据库，请自备一份同 namespace 的 XML
 *       （表名写死）并通过 {@link #idSegmentXml()} / {@link #outboxXml()} 指定。</li>
 *   <li><b>模块可关闭</b>：{@link #idEnabled()} / {@link #outboxEnabled()} 决定是否装配对应契约，
 *       引用方只想用 type handler 或只需其中一个模块时可关闭另一个。</li>
 *   <li><b>多库 XML 切换</b>：{@link #idSegmentXml()} / {@link #outboxXml()} 指向 classpath 上的
 *       SQL 实现 XML；换数据库只需提供一份 namespace 相同的 XML 并改此路径，
 *       使用方 Java 代码零改动。</li>
 * </ul>
 *
 * <p>全部字段均有合理默认值（启用 id + outbox、内置 XML），使用方按需覆盖。</p>
 */
public final class MybatisModuleOptions {


    /** 可选：type handler 上下文；非 null 时由 {@link MybatisModuleBootstrap} 在 build 后统一注册。 */
    private TypeHandlerContext typeHandlerContext;

    private MybatisModuleOptions() {
    }

    /** 返回全部字段取默认值的实例。 */
    public static MybatisModuleOptions defaults() {
        return new MybatisModuleOptions();
    }





}
