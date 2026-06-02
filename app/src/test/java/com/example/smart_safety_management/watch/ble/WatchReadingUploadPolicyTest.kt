package com.example.smart_safety_management.watch.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WatchReadingUploadPolicyTest {
    @Test
    fun ppgOnlyReadingDoesNotUpload() {
        val policy = WatchReadingUploadPolicy()

        assertFalse(policy.shouldUpload(JcWearHealthReading(ppgValue = 120), NOW))
    }

    @Test
    fun supportedReadingUploadsInitially() {
        val policy = WatchReadingUploadPolicy()

        assertTrue(policy.shouldUpload(JcWearHealthReading(heartRate = 72), NOW))
        assertTrue(policy.shouldUpload(JcWearHealthReading(bodyTemp = 36.5f), NOW.plusSeconds(1)))
        assertTrue(policy.shouldUpload(JcWearHealthReading(batteryLevel = 80), NOW.plusSeconds(2)))
    }

    @Test
    fun supportedReadingWithinOneSecondDoesNotUpload() {
        val policy = WatchReadingUploadPolicy()

        assertTrue(policy.shouldUpload(JcWearHealthReading(heartRate = 72), NOW))
        assertFalse(policy.shouldUpload(JcWearHealthReading(heartRate = 73), NOW.plusMillis(999)))
    }

    @Test
    fun supportedReadingAtOneSecondOrLaterUploads() {
        val policy = WatchReadingUploadPolicy()

        assertTrue(policy.shouldUpload(JcWearHealthReading(heartRate = 72), NOW))
        assertTrue(policy.shouldUpload(JcWearHealthReading(heartRate = 73), NOW.plusSeconds(1)))
        assertTrue(policy.shouldUpload(JcWearHealthReading(heartRate = 74), NOW.plusSeconds(2)))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-06-02T00:00:00Z")
    }
}
