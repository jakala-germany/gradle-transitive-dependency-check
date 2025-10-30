import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("maven-publish")
    id("com.gradle.plugin-publish") version "1.3.0"

    id("convention-formatting")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
    testImplementation(libs.junit5)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    website.set("https://github.com/jakala-germany/gradle-transitive-dependency-check")
    vcsUrl.set("https://github.com/jakala-germany/gradle-transitive-dependency-check.git")

    plugins {
        create("transitiveDependencyCheck") {
            id = "io.github.jakala-germany.transitive-dependency-check-gradle-plugin"
            displayName = "Transitive Dependency Check Plugin"
            description = "Ensures no declared dependency version is overridden by a newer transitive dependency"
            implementationClass = "com.jakala.transitivedependencycheck.TransitiveDependencyCheckPlugin"
            tags.set(listOf("dependencies", "transitive", "version", "conflict"))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "transitive-dependency-check-gradle-plugin"
            version = project.version.toString()

            pom {
                name.set("Transitive Dependency Check Gradle Plugin")
                description.set("A Gradle plugin to detect and fail on overridden transitive dependency versions")
                url.set("https://github.com/jakala-germany/gradle-transitive-dependency-check")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("jakala")
                        name.set("Jakala")
                        email.set("opensource@jakala.com")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/jakala-germany/gradle-transitive-dependency-check.git")
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/jakala-germany/gradle-transitive-dependency-check.git",
                    )
                    url.set("https://github.com/jakala-germany/gradle-transitive-dependency-check")
                }
            }
        }
    }
}
