# Changelog

All notable changes to Pragmatic DDD will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] — Unreleased

### Added
- New Maven coordinates: `io.pragmatic.ddd:pragmatic-ddd-parent:2.0.0`
- Java 17 as minimum required version
- Multi-module project structure (core, rocketmq, kafka, spring-boot, mybatis)
- `@DomainEntity` annotation for semantic entity metadata
- `@BusinessRule` annotation for rule method documentation
- `@EventTrigger` annotation for event trigger point documentation
- `package-info.java` with full Javadoc and usage examples
- SLF4J logging support in core module
- GitHub Actions CI workflow (JDK 17 & 21 matrix)
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`
- `IUnitOfWork` / `AbstractUnitOfWork`：统一工作单元契约与模板（与 `ICommandExecutor` / `AbstractCommandExecutor` 对称），`UnitOfWork` 改为继承模板
- `OutboxUnitOfWork`（`application.outbox`）：跨聚合根可靠投递（同事务落 outbox + 提交后推送），与 `UnitOfWork` 并存、`AbstractApplicationService` 可选注入 `Supplier<IUnitOfWork>`

### Changed
- **Breaking**: Package renamed from `cn.easylib.domain` to `io.pragmatic.ddd`
- **Breaking**: GroupId changed from `cn.easylib` to `io.pragmatic.ddd`
- **Breaking**: ArtifactId changed from `easy-domain` to `pragmatic-ddd-core`
- **Breaking**: ArtifactId changed from `easy-domainevent-rocketmq` to `pragmatic-ddd-rocketmq`
- fastjson 1.2.83 → fastjson2 2.0.53
- commons-lang3 3.4 → 3.17.0
- RocketMQ client 4.7.1 → 5.3.2
- JUnit 4 → JUnit Jupiter 5.11.4
- Added AssertJ 3.27.3 for fluent assertions
- Maven plugins upgraded to latest stable versions
- License changed from Mulan PSL 2.0 to Apache License 2.0
- `application` package renamed to `subscriber`
- **Breaking**: `ICheckRule.check(T)` → `check(T newModel, T oldModel)`（旧实体提升为方法参数，使规则无状态化、可单例）；移除 `satisfiesRule(T)` 默认方法
- **Breaking**: `IActiveRuleCondition.status(T)` → `status(T newModel, T oldModel)`；新增 `of(Function)`（不需要旧实体）与 `of(BiFunction)`（需要旧实体）两个静态适配器
- **Breaking**: `BaseRuleValidator.validate(T)` → `validate(T newModel, T oldModel)`
- `EntityRule` 去除 `cachedOldEntity` / `oldEntityLoaded` / `validatingEntity` 三个 per-call 可变字段，改为 `satisfiesRule` 内的局部变量；`supplyOldEntity()` 由无参 abstract 改为有参非 abstract（默认返回 null），新增 `requireOldEntity()` 钩子与静态 `of(Function)` 单参数适配器；删除 `getOldEntity()` / `currentEntity()`

### Removed
- `IApplication` and `BaseApplication` — deprecated, will be replaced in future version

### MyBatis module — table-name strategy changed (breaking)
- **Breaking**: Removed `MybatisModuleOptions.idSegmentTable(...)` / `outboxTable(...)` and
  `MybatisModuleOptions.variables()`; the `${idSegmentTable}` / `${outboxTable}` placeholders are gone.
- Default table names (`id_segment` / `outbox_message`) are now hard-coded directly in the built-in
  `IdSegmentMapper.xml` / `OutboxMapper.xml`, so the default usage is **zero-config** (no `<properties>`
  or variable injection needed).
- Custom table names / databases now use the existing "write a same-namespace XML" mechanism via
  `MybatisModuleOptions.idSegmentXml(...)` / `outboxXml(...)` (or Spring `<mappers>`), instead of a
  one-line config. Framework schema SQL files (`*-schema-mysql.sql`) now also hard-code the table names
  and serve purely as reference DDL.
- **Migration**: if you previously used
  `MybatisModuleOptions.defaults().idSegmentTable("x").outboxTable("y")`, replace it with a copy of
  `IdSegmentMapper.xml` / `OutboxMapper.xml` whose namespace stays the same but table name is written
  as your `x` / `y`, then point `idSegmentXml(...)` / `outboxXml(...)` at those files.

---

For changes prior to v2.0.0, see the [easy-domain releases](https://github.com/lixiaojing/easy-domain/releases).
