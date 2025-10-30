# Transitive Dependency Check Gradle Plugin

A Gradle plugin that ensures no declared dependency version is overridden by a newer transitive dependency version.

- Detects when a direct dependency you declared (e.g., commons-codec:1.10) is upgraded by a newer transitive version
  pulled in by another dependency (e.g., 1.11).
- Detects mismatches between versions of the same dependency (e.g., commons-codec) within modules (e.g., moduleA: 1.10,
  moduleB: 1.11)
- Provides a simple Gradle task named `checkTransitiveDependencies` that fails the build if such overrides or version
  mismatchings are detected.

Jump to:
[Introduction](#Introduction) |
[Getting Started](#Getting Started) |
[Example](#Example) |
[Development](#Development) |
[Contribution](#Contribution)
[Releasing](#Releasing)

## Introduction

Imagine you have `commons-codec:1.10` and `httpclient:4.5.13` implemented and your commons-codec usage no longer works
because there are breaking changes within 1.11 which is transitively used by `httpclient:4.5.13`. This gradle plugin
detects such transitive dependency updates and informs you about it. As gradle automatically picks the highest number
you have `commons-codec:1.11` within your project and there is no reason for you to declare version `1.10` anymore.

Traversal dependency update detection:

```
+--- project :module:one
|    \--- org.apache.httpcomponents:httpclient:4.5.13
|    |    \--- commons-codec:commons-codec:1.11
+--- project :module:two
|    \--- commons-codec:commons-codec:1.10 -> 1.11 <-- Detects these traversal dependency updates
```

```
+--- project :sample
|    \--- commons-codec:commons-codec:1.10 -> 1.11 <-- Detects these traversal dependency updates
|    \--- org.apache.httpcomponents:httpclient:4.5.13
|    |    \--- commons-codec:commons-codec:1.11
```

Dependency version mismatch detection:

```
+--- project :module:one
|    \--- commons-codec:commons-codec:1.11
+--- project :module:two
|    \--- commons-codec:commons-codec:1.10 <-- Detects these dependency version mismatches
```

## Getting Started

Apply the plugin in your root build.gradle using the Gradle Plugins DSL:

```kotlin
plugins {
    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin") version "<latest-version>"
}
```

Or with Groovy DSL:

```groovy
plugins {
    id 'io.github.jakala-germany.transitive-dependency-check-gradle-plugin' version '<latest-version>'
}
```

Then execute:

```bash
./gradlew checkAggregatedTransitiveDependencies
```

If problems are found, the task will fail with a report of the conflicts.

Or if you want to check the transitive dependencies on a single project execute:

```bash
./gradlew :myProject:checkTransitiveDependencies
```

## Example

Given this dependency setup:

```kotlin
dependencies {
    implementation("org.apache.httpcomponents:httpclient:4.5.13") // pulls commons-codec:1.11 transitively
    implementation("commons-codec:commons-codec:1.10")            // declared lower version -> will be overridden
}
```

Running `./gradlew checkTransitiveDependencies` will fail and report something like:

```
Some dependencies were upgraded transitively.
commons-codec:commons-codec declared with 1.10 → resolved as 1.11
```

## Development

Prerequisites:

- JDK 17+
- Internet access to resolve dependencies

Build and test locally:

```bash
./gradlew build
```

Run functional tests:

```bash
./gradlew check
```

### Try it in a sample project

Publish the plugin to your local Maven repository and test it in another build:

```bash
./gradlew :plugin:publishToMavenLocal
```

Then in your test project build.gradle(.kts):

```kotlin
plugins {
    id("io.github.jakala-germany.transitive-dependency-check-gradle-plugin") version "1.0.0-SNAPSHOT"
}
```

## Contribution

We welcome contributions of all kinds! Please:

- Read the contributing guide: see [CONTRIBUTING.md](CONTRIBUTING.md).
- Follow our [Code of Conduct](CODE_OF_CONDUCT.md).
- Typical workflow:
    - Fork the repository and create a feature branch from main.
    - Implement your changes and add/adjust tests when possible.
    - Run the full build locally: `./gradlew build` and ensure checks pass.
    - Update documentation if needed.
    - Open a Pull Request describing your change.
- For security issues, please follow our [Security Policy](SECURITY.md).

## Releasing

### Publish to local Maven

- Run: `./gradlew :plugin:publishToMavenLocal`
- Apply plugin with version `1.0.0-SNAPSHOT` from your local projects.

### Publish to Maven Central

- See [RELEASE.md](RELEASE.md) for detailed instructions.
