package com.facefacecamera

import com.facefacecamera.facefx.FaceFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceFilterPresetTest {
    @Test
    fun defaultsProvideExpectedShowcaseSet() {
        val presets = FaceFilterPreset.defaults()

        assertEquals(4, presets.size)
        assertEquals("square", presets.first().id)
        assertTrue(presets.any { it.id == "peak" })
        assertTrue(presets.any { it.id == "blade" })
    }
}
