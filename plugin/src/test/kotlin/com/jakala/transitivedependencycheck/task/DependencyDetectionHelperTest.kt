package com.jakala.transitivedependencycheck.task

import com.jakala.transitivedependencycheck.model.DependencyGroupName
import com.jakala.transitivedependencycheck.model.DependencyVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DependencyDetectionHelperTest {
    @Nested
    inner class CompareVersions {
        @Test
        fun `equal versions return zero`() {
            assertEquals(0, compare("1.2.3", "1.2.3"))
        }

        @Test
        fun `higher patch version is greater`() {
            assertTrue(compare("1.2.4", "1.2.3") > 0)
            assertTrue(compare("1.2.3", "1.2.4") < 0)
        }

        @Test
        fun `higher minor version is greater`() {
            assertTrue(compare("1.3.0", "1.2.9") > 0)
        }

        @Test
        fun `higher major version is greater`() {
            assertTrue(compare("2.0.0", "1.9.9") > 0)
        }

        @Test
        fun `shorter version treats missing segments as zero`() {
            // 1.0 == 1.0.0 under current naive comparator
            assertEquals(0, compare("1.0", "1.0.0"))
        }

        @Test
        fun `non-numeric qualifiers are treated as zero`() {
            // documents current behaviour: alpha and beta both become 0
            assertEquals(0, compare("1.0-alpha", "1.0-beta"))
        }

        @Test
        fun `snapshot qualifier is treated as zero`() {
            // documents current behaviour: SNAPSHOT → 0, equals plain segment 0
            assertEquals(0, compare("1.0.0-SNAPSHOT", "1.0.0"))
        }

        @Test
        fun `dash-separated segments are compared individually`() {
            assertTrue(compare("1.0-2", "1.0-1") > 0)
        }

        private fun compare(a: String, b: String) =
            DependencyDetectionHelper.compareVersions(DependencyVersion(a), DependencyVersion(b))
    }

    @Nested
    inner class IsRelevantClasspath {
        @Test
        fun `compileClasspath matches`() {
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("compileClasspath"))
        }

        @Test
        fun `runtimeClasspath matches`() {
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("runtimeClasspath"))
        }

        @Test
        fun `source-set-prefixed compileClasspath matches`() {
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("testCompileClasspath"))
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("integrationTestRuntimeClasspath"))
        }

        @Test
        fun `android variant prefixed runtimeClasspath matches`() {
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("debugRuntimeClasspath"))
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("releaseCompileClasspath"))
        }

        @Test
        fun `matching is case-insensitive`() {
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("CompileClasspath"))
            assertTrue(DependencyDetectionHelper.isRelevantClasspath("RUNTIMECLASSPATH"))
        }

        @Test
        fun `unrelated configuration does not match`() {
            assertFalse(DependencyDetectionHelper.isRelevantClasspath("apiElements"))
            assertFalse(DependencyDetectionHelper.isRelevantClasspath("runtimeElements"))
            assertFalse(DependencyDetectionHelper.isRelevantClasspath("implementation"))
            assertFalse(DependencyDetectionHelper.isRelevantClasspath("compileOnly"))
        }
    }

    @Nested
    inner class DetectDeclaredVersionMismatches {
        private val ga = DependencyGroupName("com.example:foo")

        @Test
        fun `single version returns empty`() {
            val result = DependencyDetectionHelper.detectDeclaredVersionMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"))),
                exclusions = emptyList(),
            )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `multiple versions returns violation`() {
            val result = DependencyDetectionHelper.detectDeclaredVersionMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"), DependencyVersion("2.0"))),
                exclusions = emptyList(),
            )
            assertEquals(1, result.size)
            assertTrue(result[0].contains("com.example:foo"))
        }

        @Test
        fun `all versions matching exclusion returns empty`() {
            val result = DependencyDetectionHelper.detectDeclaredVersionMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"), DependencyVersion("2.0"))),
                exclusions = listOf(Regex("com\\.example:foo:.*")),
            )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `partial exclusion still reports violation`() {
            val result = DependencyDetectionHelper.detectDeclaredVersionMismatches(
                declared = mapOf(
                    ga to setOf(DependencyVersion("1.0"), DependencyVersion("2.0")),
                ),
                exclusions = listOf(Regex("com\\.example:foo:1\\.0")),
            )
            // 2.0 is not excluded, so the group is still reported
            assertEquals(1, result.size)
        }
    }

    @Nested
    inner class DetectResolvedUpgradeMismatches {
        private val ga = DependencyGroupName("com.example:foo")

        @Test
        fun `declared equals resolved returns empty`() {
            val result = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"))),
                resolved = mapOf(ga to DependencyVersion("1.0")),
                exclusions = emptyList(),
            )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `resolved is lower than declared returns empty`() {
            val result = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("2.0"))),
                resolved = mapOf(ga to DependencyVersion("1.0")),
                exclusions = emptyList(),
            )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `resolved is higher than declared returns violation`() {
            val result = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"))),
                resolved = mapOf(ga to DependencyVersion("2.0")),
                exclusions = emptyList(),
            )
            assertEquals(1, result.size)
            assertTrue(result[0].contains("1.0 → resolved as 2.0"))
        }

        @Test
        fun `multiple declared versions short-circuits`() {
            val result = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"), DependencyVersion("1.1"))),
                resolved = mapOf(ga to DependencyVersion("2.0")),
                exclusions = emptyList(),
            )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `exclusion match suppresses violation`() {
            val result = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"))),
                resolved = mapOf(ga to DependencyVersion("2.0")),
                exclusions = listOf(Regex("com\\.example:foo:1\\.0")),
            )
            assertTrue(result.isEmpty())
        }

        @Test
        fun `no resolved entry returns empty`() {
            val result = DependencyDetectionHelper.detectResolvedUpgradeMismatches(
                declared = mapOf(ga to setOf(DependencyVersion("1.0"))),
                resolved = emptyMap(),
                exclusions = emptyList(),
            )
            assertTrue(result.isEmpty())
        }
    }
}
