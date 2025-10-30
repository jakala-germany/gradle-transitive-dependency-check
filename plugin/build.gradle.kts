import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URI

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    alias(libs.plugins.publish)

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
    repositories {
        maven {
            name = "testing"
            url = URI.create("${rootProject.projectDir}/build/localMaven")
        }
    }
}
