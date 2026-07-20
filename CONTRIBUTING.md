# Contributing to Pragmatic DDD

Thank you for your interest in contributing to Pragmatic DDD! 🎉

## Code of Conduct

This project and everyone participating in it is governed by our [Code of Conduct](./CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the [existing issues](https://github.com/lixiaojing/pragmatic-ddd/issues) to avoid duplicates. When reporting a bug, include:

- **Version**: The version of Pragmatic DDD you are using
- **JDK Version**: e.g., JDK 17, JDK 21
- **Steps to Reproduce**: Minimal code to reproduce the issue
- **Expected vs Actual Behavior**
- **Stack Trace** (if applicable)

### Suggesting Enhancements

Enhancement suggestions are tracked as [GitHub Issues](https://github.com/lixiaojing/pragmatic-ddd/issues). When suggesting an enhancement:

- Describe the use case in detail
- Provide a code example of the desired API
- Explain why this enhancement would be useful to others

### Pull Requests

1. Fork the repository and create your branch from `main`
2. If you've added code, add tests that cover it
3. Ensure the test suite passes: `mvn clean test`
4. Follow the coding style used throughout the project
5. Update documentation if necessary
6. Make sure your commits follow [Conventional Commits](https://www.conventionalcommits.org/)

#### Development Setup

```bash
git clone https://github.com/lixiaojing/pragmatic-ddd.git
cd pragmatic-ddd
mvn clean compile
```

#### Running Tests

```bash
mvn clean test
```

### Coding Style

- **Java Version**: The project targets Java 17
- **Package Structure**: Follow the existing module structure
- **Naming**:
  - Entities: `<Name>` (e.g., `Order`)
  - Events: `<Entity><ActionPastTense>Event` (e.g., `OrderPayedEvent`)
  - Rules: `<Entity>EntityRule` (e.g., `OrderEntityRule`)
  - Broken Rule Messages: `<Entity>BrokenRuleMessages`
- **Annotations**: Use `@DomainEntity`, `@BusinessRule`, `@EventTrigger` where applicable
- No `@Data` on entities — use explicit getters
- No database operations in domain layer
- No Spring annotations in domain layer

## Developer Certificate of Origin (DCO)

By contributing, you agree to the [Developer Certificate of Origin](https://developercertificate.org/):

```
Developer Certificate of Origin
Version 1.1

Copyright (C) 2004, 2006 The Linux Foundation and its contributors.

By making a contribution to this project, I certify that:

(a) The contribution was created in whole or in part by me and I
    have the right to submit it under the open source license
    indicated in the file; or

(b) The contribution is based upon previous work that, to the best
    of my knowledge, is covered under an appropriate open source
    license and I have the right under that license to submit that
    work with modifications, whether created in whole or in part
    by me, under the same open source license (unless I am
    permitted to submit under a different license), as indicated
    in the file; or

(c) The contribution was provided directly to me by some other
    person who certified (a), (b) or (c) and I have not modified
    it.

(d) I understand and agree that this project and the contribution
    are public and that a record of the contribution (including all
    personal information I submit with it, including my sign-off) is
    maintained indefinitely and may be redistributed consistent with
    this project or the open source license(s) involved.
```

Sign your commits with `git commit -s` to add a Signed-off-by line.

## Questions?

Open a [GitHub Discussion](https://github.com/lixiaojing/pragmatic-ddd/discussions) or [create an issue](https://github.com/lixiaojing/pragmatic-ddd/issues).
