package com.janus.coordmapping

/**
 * Describes the actual rectangle, within a rendering surface, where video
 * content is drawn — as opposed to the surface's full bounds.
 *
 * This distinction is the entire point of spec #29 ("pixel-perfect
 * coordinate mapping"): a SurfaceView/TextureView is typically sized to fill
 * available layout space, but the video frame drawn inside it is scaled to
 * fit while preserving aspect ratio (letterbox/pillarbox), so the actual
 * content rarely fills 100% of the surface. Any touch-to-Target coordinate
 * transform that ignores this — and just scales by surface width/height —
 * will be wrong whenever the Target's aspect ratio doesn't exactly match the
 * surface's aspect ratio, which is the common case (e.g. portrait phone
 * Target streamed into a differently-proportioned Controller viewport).
 *
 * All values are in the same pixel space as the surface that hosts the
 * rendered content (i.e. Controller-side view/surface pixels), not Target
 * pixels — CoordinateTransform.kt is what converts between this space and
 * the Target's actual pixel resolution.
 *
 * @param offsetX left edge of the drawn content, relative to the surface's
 *   left edge. Non-zero when there is pillarboxing (empty space on left/right).
 * @param offsetY top edge of the drawn content, relative to the surface's
 *   top edge. Non-zero when there is letterboxing (empty space top/bottom).
 * @param contentWidth width of the actually-drawn video content, in surface
 *   pixels — NOT the surface's own width.
 * @param contentHeight height of the actually-drawn video content, in
 *   surface pixels — NOT the surface's own height.
 */
data class ContentRect(
    val offsetX: Float,
    val offsetY: Float,
    val contentWidth: Float,
    val contentHeight: Float
) {
    init {
        require(contentWidth > 0f) { "contentWidth must be positive, was $contentWidth" }
        require(contentHeight > 0f) { "contentHeight must be positive, was $contentHeight" }
    }

    /** True if the given surface-space point falls within the drawn content area. */
    fun contains(x: Float, y: Float): Boolean {
        return x >= offsetX &&
            x <= offsetX + contentWidth &&
            y >= offsetY &&
            y <= offsetY + contentHeight
    }

    companion object {
        /**
         * Computes the letterboxed/pillarboxed content rectangle for fitting
         * a [sourceWidth]x[sourceHeight] video (e.g. the Target's resolution)
         * inside a [surfaceWidth]x[surfaceHeight] rendering surface while
         * preserving aspect ratio — equivalent to how a video player's
         * "fit/contain" scale mode positions its content, matching spec #28's
         * requirement that the video fill available space without ugly
         * permanent letterboxing beyond what aspect-ratio preservation
         * actually requires.
         */
        fun computeFitContent(
            surfaceWidth: Float,
            surfaceHeight: Float,
            sourceWidth: Float,
            sourceHeight: Float
        ): ContentRect {
            require(surfaceWidth > 0f && surfaceHeight > 0f) {
                "surface dimensions must be positive"
            }
            require(sourceWidth > 0f && sourceHeight > 0f) {
                "source dimensions must be positive"
            }

            val surfaceAspect = surfaceWidth / surfaceHeight
            val sourceAspect = sourceWidth / sourceHeight

            return if (sourceAspect > surfaceAspect) {
                // Source is relatively wider than the surface -> full width,
                // letterboxed top/bottom.
                val contentWidth = surfaceWidth
                val contentHeight = surfaceWidth / sourceAspect
                val offsetY = (surfaceHeight - contentHeight) / 2f
                ContentRect(
                    offsetX = 0f,
                    offsetY = offsetY,
                    contentWidth = contentWidth,
                    contentHeight = contentHeight
                )
            } else {
                // Source is relatively taller than the surface -> full
                // height, pillarboxed left/right.
                val contentHeight = surfaceHeight
                val contentWidth = surfaceHeight * sourceAspect
                val offsetX = (surfaceWidth - contentWidth) / 2f
                ContentRect(
                    offsetX = offsetX,
                    offsetY = 0f,
                    contentWidth = contentWidth,
                    contentHeight = contentHeight
                )
            }
        }
    }
}