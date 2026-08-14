# Changelog

## [Snapshot]

[Snapshot]: https://github.com/jakala-germany/gradle-transitive-dependency-check/compare/1.2.0...HEAD

**New**

- Upcoming additions go here

**Changed**

- Upcoming changes go here

## [1.2.0] - 2026-08-14

[1.2.0]: https://github.com/jakala-germany/gradle-transitive-dependency-check/releases/tag/1.2.0

**New**

- Configuration cache support: both tasks are now fully compatible with `--configuration-cache`
- `CheckTransitiveDependenciesTask` is now `@CacheableTask`; outputs are restored from the build cache on repeat runs

**Changed**

- `TransitiveDependencyCheckExtension` is the corrected spelling of the public extension interface
- Resolution graph traversal failures are now logged at `WARN` level instead of `INFO`
- Dependency bumps: Kotlin 2.3.21, ktlint plugin 14.2.0, ktlint runtime 1.8.0,
  JUnit 5.12.2, com.vanniktech.maven.publish 0.36.0, Gradle 9.6.1

## [1.1.0] - 2026-04-30

[1.1.0]: https://github.com/jakala-germany/gradle-transitive-dependency-check/releases/tag/1.1.0

**Changed**

- Skip plugin-internal tool configurations (e.g. ktlint, detekt) when collecting declared dependencies, eliminating
  false-positive version-mismatch and transitive-upgrade violations for libraries the user does not declare directly

## [1.0.0] - 2025-10-31

[1.0.0]: https://github.com/jakala-germany/gradle-transitive-dependency-check/releases/tag/1.0.0

**New**

- Initial public release
- Gradle plugin registering `checkAggregatedTransitiveDependencies` (root) & `checkTransitiveDependencies` (each
  project) tasks
- Functional test verifying failure on overridden transitive versions
- Documentation
