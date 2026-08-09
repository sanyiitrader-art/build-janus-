package com.janus.coordmapping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Deterministic unit tests for CoordinateTransform, per spec #54.
 * Run locally / in CI with: ./gradlew :coordmapping:test
 * No Android SDK, emulator, or device required.
 */
class CoordinateTransformTest {

    @Test
    fun `spec example - exact aspect ratio match, no letterboxing`() {
        // Target: 1080x2400 (aspect 0.45). Controller content: 360x800
        // (aspect 0.45) -- same aspect ratio, so contentRect exactly fills
        // the surface with zero offset. Touch at 180x400 is the exact
        // center of the surface, so it must map to the exact center of the
        // Target: (540, 1200).
        val contentRect = ContentRect(
            offsetX = 0f,
            offsetY = 0f,
            contentWidth = 360f,
            contentHeight = 800f
        )

        val result = CoordinateTransform.surfaceToTarget(
            touchX = 180f,
            touchY = 400f,
            contentRect = contentRect,
            targetWidth = 1080,
            targetHeight = 2400
        )

        assertEquals(TargetPoint(540, 1200), result)
    }

    @Test
    fun `top-left corner maps to target top-left`() {
        val contentRect = ContentRect(0f, 0f, 360f, 800f)
        val result = CoordinateTransform.surfaceToTarget(
            touchX = 0f,
            touchY = 0f,
            contentRect = contentRect,
            targetWidth = 1080,
            targetHeight = 2400
        )
        assertEquals(TargetPoint(0, 0), result)
    }

    @Test
    fun `bottom-right corner maps to target bottom-right, clamped inside bounds`() {
        val contentRect = ContentRect(0f, 0f, 360f, 800f)
        val result = CoordinateTransform.surfaceToTarget(
            touchX = 360f,
            touchY = 800f,
            contentRect = contentRect,
            targetWidth = 1080,
            targetHeight = 2400
        )
        // Coerced to (width-1, height-1) since pixel indices are 0-based.
        assertEquals(TargetPoint(1079, 2399), result)
    }

    @Test
    fun `letterboxed content - wider surface than target aspect ratio`() {
        // Target is a 1080x2400 portrait phone (aspect 0.45). Surface is
        // 400x400 (square). The video fits by height, pillarboxed left/right.
        val contentRect = ContentRect.computeFitContent(
            surfaceWidth = 400f,
            surfaceHeight = 400f,
            sourceWidth = 1080f,
            sourceHeight = 2400f
        )
        assertEquals(110f, contentRect.offsetX, 0.01f)
        assertEquals(0f, contentRect.offsetY, 0.01f)
        assertEquals(180f, contentRect.contentWidth, 0.01f)
        assertEquals(400f, contentRect.contentHeight, 0.01f)

        // A touch at the exact center of the surface (200, 200) should map
        // to the exact center of the Target (540, 1200).
        val result = CoordinateTransform.surfaceToTarget(
            touchX = 200f,
            touchY = 200f,
            contentRect = contentRect,
            targetWidth = 1080,
            targetHeight = 2400
        )
        assertEquals(TargetPoint(540, 1200), result)
    }

    @Test
    fun `touch on pillarbox dead zone clamps to nearest content edge by default`() {
        val contentRect = ContentRect(offsetX = 110f, offsetY = 0f, contentWidth = 180f, contentHeight = 400f)
        // Touch at x=0 is in the left pillarbox dead zone (before offsetX=110).
        val result = CoordinateTransform.surfaceToTarget(
            touchX = 0f,
            touchY = 200f,
            contentRect = contentRect,
            targetWidth = 1080,
            targetHeight = 2400,
            clampToBounds = true
        )
        // Clamped to the left edge of content -> targetX = 0
        assertEquals(0, result!!.x)
    }

    @Test
    fun `touch on pillarbox dead zone returns null when clamping disabled`() {
        val contentRect = ContentRect(offsetX = 110f, offsetY = 0f, contentWidth = 180f, contentHeight = 400f)
        val result = CoordinateTransform.surfaceToTarget(
            touchX = 0f,
            touchY = 200f,
            contentRect = contentRect,
            targetWidth = 1080,
            targetHeight = 2400,
            clampToBounds = false
        )
        assertNull(result)
    }

    @Test
    fun `landscape target - rotation changes aspect ratio, mapping still correct`() {
        // Simulates a Target that rotated from portrait (1080x2400) to
        // landscape (2400x1080). Surface stays 800x400 to match.
        val contentRect = ContentRect.computeFitContent(
            surfaceWidth = 800f,
            surfaceHeight = 400f,
            sourceWidth = 2400f,
            sourceHeight = 1080f
        )
        val result = CoordinateTransform.surfaceToTarget(
            touchX = 400f, // exact horizontal center
            touchY = 200f, // exact vertical center
            contentRect = contentRect,
            targetWidth = 2400,
            targetHeight = 1080
        )
        assertEquals(TargetPoint(1200, 540), result)
    }

    @Test
    fun `non-square pixel target resolution maps proportionally`() {
        val contentRect = ContentRect(0f, 0f, 100f, 100f)
        val result = CoordinateTransform.surfaceToTarget(
            touchX = 25f,
            touchY = 75f,
            contentRect = contentRect,
            targetWidth = 400,
            targetHeight = 200
        )
        // 25% across -> 25% of 400 = 100; 75% down -> 75% of 200 = 150
        assertEquals(TargetPoint(100, 150), result)
    }
}