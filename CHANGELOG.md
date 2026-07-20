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

### Removed
- `IApplication` and `BaseApplication` — deprecated, will be replaced in future version

---

For changes prior to v2.0.0, see the [easy-domain releases](https://github.com/lixiaojing/easy-domain/releases).
