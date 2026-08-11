package io.legado.enhance.explore

/** Host-neutral representation of one Legado explore entry. */
data class ExploreEntry(
    val title: String,
    val url: String? = null,
    val action: String? = null,
    val type: String = "url",
    val defaultValue: String? = null,
    val children: List<ExploreEntry> = emptyList(),
)

