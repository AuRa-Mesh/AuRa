package com.example.aura.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BeaconShareLinkCoordinateParseTest {

    @Test
    fun parse_plain_us_format() {
        val p = BeaconShareLink.parseCoordinatesOnlyMessage("55.123456, 37.654321")
        assertNotNull(p)
        assertEquals(55.123456, p!!.first, 1e-9)
        assertEquals(37.654321, p.second, 1e-9)
    }

    @Test
    fun parse_strips_zwsp_from_paste() {
        val pasted = "\u200B55.123456,\u200B 37.654321"
        assertNull(
            "sanity: without normalization this shape used to fail matchEntire",
            Regex("""^(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)$""").matchEntire(pasted.trim()),
        )
        val p = BeaconShareLink.parseCoordinatesOnlyMessage(pasted)
        assertNotNull(p)
        assertEquals(55.123456, p!!.first, 1e-9)
        assertEquals(37.654321, p.second, 1e-9)
    }

    @Test
    fun parse_nbsp_and_bom() {
        val p = BeaconShareLink.parseCoordinatesOnlyMessage("\uFEFF55.0,\u00A037.0")
        assertNotNull(p)
        assertEquals(55.0, p!!.first, 1e-12)
        assertEquals(37.0, p.second, 1e-12)
    }

    @Test
    fun parse_semicolon() {
        val p = BeaconShareLink.parseCoordinatesOnlyMessage("55.0;37.1")
        assertNotNull(p)
        assertEquals(55.0, p!!.first, 1e-12)
        assertEquals(37.1, p.second, 1e-12)
    }

    @Test
    fun parse_outer_quotes() {
        val p = BeaconShareLink.parseCoordinatesOnlyMessage("\"55.0, 37.1\"")
        assertNotNull(p)
        assertEquals(55.0, p!!.first, 1e-12)
        assertEquals(37.1, p.second, 1e-12)
    }
}
