package com.example.smart_safety_management.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Phase 11 / 11-01 — common_toolbar.xml characterization test.
 * Robolectric 미사용 — XML 파일을 plain text 로 읽어 substring 검증.
 *
 * cwd 진단: Gradle 의 Android module JVM test cwd 는 일반적으로 `app/`.
 * File 상대경로 'src/main/res/layout/common_toolbar.xml' 가 resolve 되어야 함.
 */
class CommonToolbarXmlTest {
    private val xml: File by lazy {
        val rel = File("src/main/res/layout/common_toolbar.xml")
        if (rel.exists()) rel else File("app/src/main/res/layout/common_toolbar.xml")
    }

    @Test fun commonToolbarXml_exists() {
        assertTrue(
            "common_toolbar.xml not found. cwd=${System.getProperty("user.dir")} resolved=${xml.absolutePath}",
            xml.exists(),
        )
    }
    @Test fun commonToolbarXml_containsToolbarWidget() {
        assertTrue("Toolbar widget tag missing", xml.readText().contains("Toolbar"))
    }
    @Test fun commonToolbarXml_hasToolbarId() {
        assertTrue("@+id/toolbar missing", xml.readText().contains("@+id/toolbar"))
    }
    @Test fun commonToolbarXml_hasNavigationIcon() {
        assertTrue("navigationIcon attribute missing", xml.readText().contains("navigationIcon"))
    }
    @Test fun commonToolbarXml_hasHeightActionBarSize() {
        assertTrue("?attr/actionBarSize height missing", xml.readText().contains("?attr/actionBarSize"))
    }
}
