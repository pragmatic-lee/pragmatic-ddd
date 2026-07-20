# Security Policy

## Supported Versions

We release patches for security vulnerabilities. The following versions are currently supported:

| Version | Supported          |
|---------|--------------------|
| 2.0.x   | ✅ Active support  |
| < 2.0   | ❌ No longer supported |

## Reporting a Vulnerability

We take the security of Pragmatic DDD seriously. If you believe you have found a security vulnerability, please report it to us as described below.

**Please do NOT report security vulnerabilities through public GitHub issues.**

Instead, please report them via email to:

📧 **lee.bmw@foxmail.com**

Please include the following information in your report:

- A description of the vulnerability
- Steps to reproduce the issue
- The affected version(s)
- Any potential mitigations you've identified

### What to Expect

- **Acknowledgment**: We will acknowledge receipt of your vulnerability report within 48 hours.
- **Assessment**: We will assess the issue and determine the severity within 5 business days.
- **Fix Timeline**: We aim to release a fix for critical vulnerabilities within 7 days.
- **Disclosure**: We will coordinate public disclosure with you once a fix is available.

## Dependency Security

We use tools to monitor our dependencies for known vulnerabilities:

- Dependabot for automated dependency updates
- Regular dependency scanning in CI pipeline

If you discover a vulnerability in one of our dependencies, please report it following the process above.

## Secure Development Practices

- All code changes are reviewed via Pull Requests
- CI pipeline includes static analysis checks
- Dependencies are pinned to specific versions
- Releases are signed with GPG keys
