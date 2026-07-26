package gov.anzong.androidnga

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWorkflowContractTest {
    private val repositoryRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, ".github/workflows/build.yml").isFile }

    @Test
    fun previewBuildKeepsProductionIdentityAndIsDebuggableWithoutMinification() {
        val gradle = File(repositoryRoot, "nga_phone_base_3.0/build.gradle").readText()
        val release = buildTypeBlock(gradle, "release")
        val debug = buildTypeBlock(gradle, "debug")
        val preview = buildTypeBlock(gradle, "preview")

        assertTrue(gradle.contains("applicationId \"com.github.tophtab.ngajustworks\""))
        assertTrue(debug.contains("applicationIdSuffix '.debug'"))
        assertTrue(preview.contains("initWith release"))
        assertTrue(preview.contains("matchingFallbacks = ['release']"))
        assertTrue(preview.contains("debuggable true"))
        assertTrue(preview.contains("minifyEnabled false"))
        assertTrue(preview.contains("signingConfig signingConfigs.release"))
        assertFalse(preview.contains("applicationIdSuffix"))

        assertTrue(release.contains("debuggable false"))
        assertTrue(release.contains("jniDebuggable false"))
        assertTrue(release.contains("renderscriptDebuggable false"))
        assertTrue(release.contains("minifyEnabled true"))
        assertTrue(release.contains("signingConfig signingConfigs.release"))
    }

    @Test
    fun rootGradleAcceptsDebugDistributionNamesAndRejectsLegacyPreviewNames() {
        val gradle = File(repositoryRoot, "build.gradle").readText()

        assertTrue(gradle.contains("(?:-debug\\.[0-9]+)?"))
        assertFalse(gradle.contains("-preview"))
    }

    @Test
    fun mainPublishesDebugNamedPrereleaseAndTagsPublishStableRelease() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()

        assertTrue(workflow.contains("version_name=\"\${stable_base}-debug.\${GITHUB_RUN_NUMBER}\""))
        assertTrue(workflow.contains("release_tag=\"debug-\${short_sha}\""))
        assertTrue(workflow.contains("release_title=\"NGA Just Works \${version_name} (Debug)\""))
        assertTrue(workflow.contains("release_title=\"NGA Just Works \$GITHUB_REF_NAME\""))
        assertTrue(workflow.contains("gradle_task=assemblePreview"))
        assertTrue(workflow.contains("apk_dir=preview"))
        assertTrue(workflow.contains("expected_debuggable=true"))

        assertTrue(workflow.contains("gradle_task=assembleRelease"))
        assertTrue(workflow.contains("apk_dir=release"))
        assertTrue(workflow.contains("expected_debuggable=false"))
        assertTrue(workflow.contains("release_apk=\"dist/NGA-Just-Works-\${app_version}.apk\""))
        assertFalse(workflow.contains("NGA-Just-Works-\${app_version}-debug.apk"))
        assertTrue(workflow.contains("-F prerelease=true"))
        assertTrue(workflow.contains("--prerelease"))
    }

    @Test
    fun workflowVerifiesUpgradeIdentityAndCleansLegacyAndCurrentDebugTags() {
        val workflow = File(repositoryRoot, ".github/workflows/build.yml").readText()

        assertTrue(workflow.contains("version_code=\$((4043 + GITHUB_RUN_NUMBER))"))
        assertTrue(workflow.contains("manifest application-id"))
        assertTrue(workflow.contains("com.github.tophtab.ngajustworks"))
        assertTrue(workflow.contains("manifest version-name"))
        assertTrue(workflow.contains("manifest version-code"))
        assertTrue(workflow.contains("manifest debuggable"))
        assertTrue(workflow.contains("apksigner\" verify --verbose --print-certs"))
        assertTrue(workflow.contains("test \"\${#source_apks[@]}\" -eq 1"))
        assertTrue(workflow.contains("test \"\$(find dist -maxdepth 1 -type f | wc -l)\" -eq 2"))
        assertTrue(workflow.contains("sha256sum -c ./*.sha256"))
        assertTrue(workflow.contains("select(.prerelease == true"))
        assertTrue(workflow.contains("startswith(\"preview-\")"))
        assertTrue(workflow.contains("startswith(\"debug-\")"))
        assertTrue(workflow.contains("--cleanup-tag"))
        assertTrue(workflow.contains("old_tag\" != \"\$CURRENT_DEBUG_TAG"))
    }

    private fun buildTypeBlock(gradle: String, name: String): String {
        val header = "$name {"
        val buildTypesStart = gradle.indexOf("buildTypes {")
        require(buildTypesStart >= 0) { "Missing buildTypes block" }
        val start = gradle.indexOf(header, buildTypesStart)
        require(start >= 0) { "Missing $name build type" }
        val openingBrace = gradle.indexOf('{', start)
        var depth = 0
        for (index in openingBrace until gradle.length) {
            when (gradle[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return gradle.substring(start, index + 1)
                }
            }
        }
        error("Unclosed $name build type")
    }
}
