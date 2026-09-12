package com.mentora.shared.data.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CursorPageTest {

    @Test
    fun `cursor page carries a next cursor when more pages remain`() {
        val page = CursorPage(items = listOf("a", "b"), nextCursor = "cursor-2")

        assertEquals(listOf("a", "b"), page.items)
        assertEquals("cursor-2", page.nextCursor)
    }

    @Test
    fun `cursor page has a null next cursor on the last page`() {
        val page = CursorPage(items = listOf("a", "b"), nextCursor = null)

        assertNull(page.nextCursor)
    }
}
