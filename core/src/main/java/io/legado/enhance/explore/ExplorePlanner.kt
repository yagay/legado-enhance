package io.legado.enhance.explore

/** Pure policy code. Android and Legado-specific loading stay in the host app. */
object ExplorePlanner {
    fun detect(entries: List<ExploreEntry>): ExploreMode = when {
        entries.any { it.children.isNotEmpty() } -> ExploreMode.TREE
        entries.any { it.type == "text" && it.url.isNullOrBlank() } -> ExploreMode.SECTION
        else -> ExploreMode.FLAT
    }

    fun build(entries: List<ExploreEntry>): List<ExploreNode> = build(entries, 0, emptyList())

    private fun build(
        entries: List<ExploreEntry>,
        level: Int,
        parentPath: List<String>,
    ): List<ExploreNode> = entries.map { entry ->
        val path = parentPath + entry.title
        ExploreNode(
            title = entry.title,
            url = entry.action ?: entry.url,
            type = entry.type,
            level = level,
            path = path,
            children = build(entry.children, level + 1, path),
        )
    }

    fun flatten(nodes: List<ExploreNode>): List<ExploreNode> = buildList {
        fun append(items: List<ExploreNode>) {
            items.forEach {
                add(it)
                append(it.children)
            }
        }
        append(nodes)
    }
}

