package com.example.smart_safety_management

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Issue 2A 회귀 가드 — DetectionEventDTO 의 image_url 이 EventData 까지 propagate
 * 되는지 검증. AsyncImage thumbnail 표시의 전제조건.
 */
class EventDataMappingTest {

    @Test fun dtoWithImageUrl_propagatesToEventData() {
        val dto = DetectionEventDTO(
            eventId = 42,
            riskLevel = "DANGER",
            installArea = "장소",
            eventName = "person",
            detectedAt = "2026-05-27 10:00:00",
            deviceName = "cam01",
            accuracy = 0.92,
            status = "PENDING",
            workerName = null,
            actionTime = null,
            imageUrl = "https://example.com/capture.jpg"
        )
        val ed = dto.toEventData()
        assertEquals("https://example.com/capture.jpg", ed.imageUrl)
    }

    @Test fun dtoWithNullImageUrl_eventDataImageUrlIsNull() {
        val dto = DetectionEventDTO(
            eventId = 1,
            riskLevel = "WARNING",
            installArea = null,
            eventName = "smoke",
            detectedAt = "2026-05-27 10:00:00",
            deviceName = null,
            accuracy = null,
            status = "PENDING",
            workerName = null,
            actionTime = null,
            imageUrl = null
        )
        assertNull(dto.toEventData().imageUrl)
    }

    @Test fun dtoWithoutImageUrlArg_defaultsToNull() {
        // backward compat: 기존 호출 (image_url 없이 생성) 도 null 로 정상 작동
        val dto = DetectionEventDTO(
            eventId = 1,
            riskLevel = "WARNING",
            installArea = null,
            eventName = "smoke",
            detectedAt = "2026-05-27 10:00:00",
            deviceName = null,
            accuracy = null,
            status = "PENDING",
            workerName = null,
            actionTime = null
        )
        assertNull(dto.imageUrl)
        assertNull(dto.toEventData().imageUrl)
    }
}
