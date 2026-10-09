package com.dataeater.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the memory safety check.
 *
 * Getting this wrong means the app crashes on a user's phone, so it is
 * tested here rather than discovered there.
 */
class DeviceMemoryTest {

    private fun phone(totalMb: Int, availableMb: Int, low: Boolean = false) =
        DeviceMemory.Info(totalMegabytes = totalMb, availableMegabytes = availableMb, isLowMemory = low)

    /** The developer's phone. */
    private val twelveGigPhone = phone(totalMb = 11_400, availableMb = 7_000)

    @Test
    fun `a small model on a big phone is comfortable`() {
        val verdict = DeviceMemory.verdict(474, twelveGigPhone)
        assertEquals(DeviceMemory.Verdict.COMFORTABLE, verdict)
    }

    @Test
    fun `a two gig model on a big phone is comfortable`() {
        val verdict = DeviceMemory.verdict(2_400, twelveGigPhone)
        assertEquals(DeviceMemory.Verdict.COMFORTABLE, verdict)
    }

    @Test
    fun `a model bigger than all the phone memory is refused`() {
        val verdict = DeviceMemory.verdict(20_000, twelveGigPhone)
        assertEquals(DeviceMemory.Verdict.TOO_LARGE, verdict)
    }

    @Test
    fun `a model that needs more than is free is refused`() {
        // needs 6500 + 700 = 7200 MB, only 5000 free -> more than 30% short
        val verdict = DeviceMemory.verdict(6_500, phone(totalMb = 11_400, availableMb = 5_000))
        assertEquals(DeviceMemory.Verdict.TOO_LARGE, verdict)
    }

    @Test
    fun `a model that nearly fits is reported as tight but allowed`() {
        // needs 2500 + 700 = 3200 MB, 2800 free -> within 70%, so allowed
        val busyPhone = phone(totalMb = 11_400, availableMb = 2_800)
        assertEquals(DeviceMemory.Verdict.TIGHT, DeviceMemory.verdict(2_500, busyPhone))
    }

    @Test
    fun `a model may never claim more than three quarters of all memory`() {
        // needs 2400 + 700 = 3100 MB; a 3.8 GB phone only allows 2850 MB
        val smallPhone = phone(totalMb = 3_800, availableMb = 3_500)
        assertEquals(DeviceMemory.Verdict.TOO_LARGE, DeviceMemory.verdict(2_400, smallPhone))
    }

    @Test
    fun `a big model on a small phone is refused`() {
        // a 4 GB phone cannot run a 2.4 GB model plus overhead
        val smallPhone = phone(totalMb = 3_800, availableMb = 2_500)
        assertEquals(DeviceMemory.Verdict.TOO_LARGE, DeviceMemory.verdict(2_400, smallPhone))
    }

    @Test
    fun `a small model still works on a small phone`() {
        val smallPhone = phone(totalMb = 3_800, availableMb = 2_500)
        assertEquals(DeviceMemory.Verdict.COMFORTABLE, DeviceMemory.verdict(474, smallPhone))
    }

    @Test
    fun `android reporting low memory refuses anything large`() {
        val lowPhone = phone(totalMb = 11_400, availableMb = 9_000, low = true)
        assertEquals(DeviceMemory.Verdict.TOO_LARGE, DeviceMemory.verdict(1_000, lowPhone))
    }

    @Test
    fun `android reporting low memory still allows a tiny model`() {
        val lowPhone = phone(totalMb = 11_400, availableMb = 9_000, low = true)
        assertEquals(DeviceMemory.Verdict.COMFORTABLE, DeviceMemory.verdict(200, lowPhone))
    }

    @Test
    fun `every verdict has an explanation`() {
        val size = 1_000
        for (verdict in DeviceMemory.Verdict.entries) {
            val text = DeviceMemory.explain(verdict, size, twelveGigPhone)
            assertEquals(true, text.isNotBlank())
        }
    }

    @Test
    fun `the too large explanation tells the user what to do`() {
        val text = DeviceMemory.explain(
            DeviceMemory.Verdict.TOO_LARGE, 20_000, twelveGigPhone
        )
        assertEquals(true, text.contains("smaller"))
    }
}