package io.legado.enhance.explore

/**
 * Only describes how the host's existing result row should be measured.
 * Rendering, colors, typography and interaction always belong to Legado.
 */
enum class ExploreResultLayout {
    LIST,
    WATERFALL;

    fun next(): ExploreResultLayout = when (this) {
        LIST -> WATERFALL
        WATERFALL -> LIST
    }

    companion object {
        fun fromStored(value: String?): ExploreResultLayout =
            entries.firstOrNull { it.name == value } ?: LIST
    }
}

data class ExploreRowMetrics(
    val coverWidthDp: Int,
    val coverHeightDp: Int,
    val introMaxLines: Int,
)

object ExploreResultLayoutPolicy {
    fun metrics(layout: ExploreResultLayout): ExploreRowMetrics = when (layout) {
        ExploreResultLayout.LIST -> ExploreRowMetrics(
            coverWidthDp = 80,
            coverHeightDp = 110,
            introMaxLines = 3,
        )

        ExploreResultLayout.WATERFALL -> ExploreRowMetrics(
            coverWidthDp = 96,
            coverHeightDp = 136,
            introMaxLines = 5,
        )
    }
}
