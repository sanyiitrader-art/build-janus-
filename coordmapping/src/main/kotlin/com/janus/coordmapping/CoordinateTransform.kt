package com.janus.coordmapping

/**
 * Converts a touch point in Controller surface-space into the corresponding
 * Target device pixel coordinate (spec #29). This is the single source of
 * truth for that math — RemoteSurfaceView / TouchEventProcessor (Phase 8)
 * call into this object rather than reimplementing scaling inline, so the
 * logic stays independently unit-testable (spec #54) and there is exactly
 * one place a coordinate-mapping bug could live.
 *
 * Deliberately NOT `targetX = touchX * scale` (spec #29 explicitly forbids
 * this as an unproven shortcut) — instead: subtract the content rect's
 * offset first (to account for letterbox/pillarbox), THEN scale by the
 * ratio of Target resolution to actually-drawn content size, not surface
 * size.
 */
object CoordinateTransform {

    /**
     * Maps a touch point, given in the same pixel space as [contentRect]
     * (i.e. Controller surface pixels), to the corresponding point in Target
     * pixel space.
     *
     * @param touchX touch X coordinate in surface pixels
     * @param touchY touch Y coordinate in surface pixels
     * @param contentRect the actually-drawn video content rectangle within
     *   the surface (see [ContentRect.computeFitContent])
     * @param targetWidth Target device's current display width in pixels
     * @param targetHeight Target device's current display height in pixels
     * @param clampToBounds if true (default), touches outside the content
     *   rect are clamped to the nearest edge instead of returning null —
     *   appropriate for drag gestures that track slightly outside the video
     *   area. If false, out-of-bounds touches return null.
     *
     * @return the corresponding Target pixel coordinate, or null if the
     *   touch falls outside [contentRect] and [clampToBounds] is false.
     */
    fun surfaceToTarget(
        touchX: Float,
        touchY: Float,
        contentRect: ContentRect,
        targetWidth: Int,
        targetHeight: Int,
        clampToBounds: Boolean = true
    ): TargetPoint? {
        require(targetWidth > 0) { "targetWidth must be positive, was $targetWidth" }
        require(targetHeight > 0) { "targetHeight must be positive, was $targetHeight" }

        if (!clampToBounds && !contentRect.contains(touchX, touchY)) {
            return null
        }

        // Step 1: subtract content offset -> position relative to the
        // top-left of the actually-drawn video, not the surface.
        var relativeX = touchX - contentRect.offsetX
        var relativeY = touchY - contentRect.offsetY

        // Step 2 (only reached when clamping): constrain to content bounds.
        if (clampToBounds) {
            relativeX = relativeX.coerceIn(0f, contentRect.contentWidth)
            relativeY = relativeY.coerceIn(0f, contentRect.contentHeight)
        }

        // Step 3: scale by the ratio of Target resolution to the actually-
        // drawn content size (NOT the surface size — this is the part a
        // naive implementation gets wrong).
        val scaleX = targetWidth / contentRect.contentWidth
        val scaleY = targetHeight / contentRect.contentHeight

        val targetX = (relativeX * scaleX).coerceIn(0f, (targetWidth - 1).toFloat())
        val targetY = (relativeY * scaleY).coerceIn(0f, (targetHeight - 1).toFloat())

        return TargetPoint(x = targetX.toInt(), y = targetY.toInt())
    }
}

/** A resolved point in Target device pixel space, ready for input injection. */
data class TargetPoint(val x: Int, val y: Int)