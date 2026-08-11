package io.legado.enhance.explore

data class ExploreNode(
    val title: String,
    val url: String? = null,
    val type: String = "url",
    val level: Int = 0,
    val path: List<String> = emptyList(),
    val children: List<ExploreNode> = emptyList(),
) {
    val isLeaf: Boolean get() = children.isEmpty() && !url.isNullOrBlank()
}

