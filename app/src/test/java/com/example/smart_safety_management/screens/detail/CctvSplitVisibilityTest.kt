package com.example.smart_safety_management.screens.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue 1 (전경/현장 강제 split) 회귀 가드.
 *
 * `shouldShowSiteSection(overviewUrl, siteUrl)` 는 InternalDetail.kt 의
 * top-level helper. 같은 카메라(overview==site, site null, site blank) 일
 * 때 '현장' 섹션 중복 렌더 방지를 의도.
 */
class CctvSplitVisibilityTest {
    @Test fun nullSiteUrl_hidesSiteSection() {
        assertFalse(shouldShowSiteSection("rtsp://a", null))
    }

    @Test fun blankSiteUrl_hidesSiteSection() {
        assertFalse(shouldShowSiteSection("rtsp://a", ""))
        assertFalse(shouldShowSiteSection("rtsp://a", "   "))
    }

    @Test fun sameAsOverview_hidesSiteSection() {
        assertFalse(
            shouldShowSiteSection(
                "rtsp://192.168.0.13/live",
                "rtsp://192.168.0.13/live"
            )
        )
    }

    @Test fun differentUrl_showsSiteSection() {
        assertTrue(shouldShowSiteSection("rtsp://a/live", "rtsp://b/live"))
    }

    @Test fun overviewNullSiteSet_stillShows() {
        // overview 없고 site 만 있으면 site 표시 (edge case)
        assertTrue(shouldShowSiteSection(null, "rtsp://b/live"))
    }

    @Test fun bothNull_hides() {
        assertFalse(shouldShowSiteSection(null, null))
    }
}
