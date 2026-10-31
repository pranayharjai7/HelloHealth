package com.hellohealth.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class StringFormatTest {

    @Test
    fun `lowercase multi-word name is title-cased`() {
        assertEquals("Pranay Harjai", "pranay harjai".toDisplayName())
    }

    @Test
    fun `single name is capitalized`() {
        assertEquals("Pranay", "pranay".toDisplayName())
    }

    @Test
    fun `already capitalized is unchanged`() {
        assertEquals("Pranay Harjai", "Pranay Harjai".toDisplayName())
    }

    @Test
    fun `internal capitals are preserved`() {
        // Only the first char of each token is touched; the rest survives.
        assertEquals("McDonald", "McDonald".toDisplayName())
        assertEquals("O'brien", "o'brien".toDisplayName())
    }

    @Test
    fun `runs of whitespace collapse and ends trim`() {
        assertEquals("Mary Jane Watson", "  mary   jane  watson ".toDisplayName())
    }

    @Test
    fun `non-letter leading char is left as-is`() {
        assertEquals("3m Worker", "3m worker".toDisplayName())
    }

    @Test
    fun `nullable helper falls back on null and blank`() {
        val n: String? = null
        assertEquals("Healthy User", n.toDisplayNameOr("Healthy User"))
        assertEquals("Healthy User", "   ".toDisplayNameOr("Healthy User"))
    }

    @Test
    fun `nullable helper title-cases a present name`() {
        assertEquals("Pranay Harjai", "pranay harjai".toDisplayNameOr("Healthy User"))
    }
}
