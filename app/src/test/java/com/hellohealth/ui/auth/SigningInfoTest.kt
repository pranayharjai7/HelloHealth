package com.hellohealth.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks the keytool/Cloud-console fingerprint format: colon-separated uppercase two-digit hex. */
class SigningInfoTest {

    @Test
    fun `toColonHex formats bytes as colon-separated uppercase hex`() {
        val bytes = byteArrayOf(0x00, 0x0A.toByte(), 0xFF.toByte(), 0x1B)
        assertEquals("00:0A:FF:1B", toColonHex(bytes))
    }

    @Test
    fun `toColonHex pads single-digit bytes to two chars`() {
        // 0x05 must render "05", not "5" — matches keytool output exactly.
        assertEquals("05", toColonHex(byteArrayOf(0x05)))
    }

    @Test
    fun `toColonHex of empty is empty string`() {
        assertEquals("", toColonHex(byteArrayOf()))
    }
}
