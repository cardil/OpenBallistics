package org.openballistics

import org.openballistics.model.ClockDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClockDirectionTest {

    @Test
    fun validHours() {
        assertEquals(1, ClockDirection(1).hour)
        assertEquals(6, ClockDirection(6).hour)
        assertEquals(12, ClockDirection(12).hour)
    }

    @Test
    fun zeroThrows() {
        assertFailsWith<IllegalArgumentException> {
            ClockDirection(0)
        }
    }

    @Test
    fun thirteenThrows() {
        assertFailsWith<IllegalArgumentException> {
            ClockDirection(13)
        }
    }

    @Test
    fun negativeThrows() {
        assertFailsWith<IllegalArgumentException> {
            ClockDirection(-1)
        }
    }
}
