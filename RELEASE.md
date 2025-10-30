# Releasing

1. Update the `VERSION_NAME` in `gradle.properties` to the version that should be released.

2. Update the `CHANGELOG.md`:
    1. Change `Snapshot` to the corresponding release version.
    2. Add a link like in previous versions.
    3. Add `Snapshot` section back in on the top.

3. Commit via `git commit -am "Prepare release X.Y.Z"`

4. Tag via `git tag -am "Release X.Y.Z" X.Y.Z`

5. Update the `VERSION_NAME` in `gradle.properties` to the next "-SNAPSHOT" version by bumping minor version.

6. Commit via `git commit -am "Prepare next development version"`

7. Push via `git push && git push --tags`
