import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")

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

