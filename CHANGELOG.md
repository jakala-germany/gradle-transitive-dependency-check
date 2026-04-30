# Changelog

## [Snapshot]

[Snapshot]: https://github.com/jakala-germany/gradle-transitive-dependency-check/compare/1.1.0...HEAD

**Changed**

- Upcoming changes go here

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
