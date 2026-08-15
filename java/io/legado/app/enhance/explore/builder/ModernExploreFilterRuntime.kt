package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.DiscoverySuiteWidgetTarget
import io.legado.app.enhance.explore.model.ExploreMode
import io.legado.app.ui.main.explore.ExploreViewModel.DynamicSelectorUi
import kotlinx.collections.immutable.toImmutableList

/**
 * 现代发现页筛选运行时。
 *
 * 行为对齐 yagay/legado:master ExploreFragment 的现代分类：
 * - TREE：只沿当前选中路径逐层生成行；过滤纯标题/无效节点；切换上级后下级由调用方清除。
 * - SECTION：Header 是频道/分组，界面只显示“频道 + 当前频道下分类”，不会把所有 Header 递归摊平。
 * - FLAT：有效 URL 项按书源原始顺序组成单一“分类”行。
 *
 * 本类只负责把书源分类结构转换成稳定的 UI 行，不负责加载书籍。
 */
object ModernExploreFilterRuntime {

    data class Result(
        val rows: List<DynamicSelectorUi>,
        val selectedTitles: Map<String, String>,
        val currentUrl: String?,
        val mode: ExploreMode
    )

    fun build(
        kinds: List<ExploreKind>,
        sourceUrl: String,
        selections: Map<String, String>,
        persistedSelections: Map<String, String> = emptyMap()
    ): Result {
        val mode = detectMode(kinds)
        return when (mode) {
            ExploreMode.TREE -> buildTree(kinds, sourceUrl, selections, persistedSelections)
            ExploreMode.SECTION -> buildSection(kinds, sourceUrl, selections, persistedSelections)
            ExploreMode.FLAT -> buildFlat(kinds, sourceUrl, selections, persistedSelections)
        }
    }

    private fun buildTree(
        kinds: List<ExploreKind>,
        sourceUrl: String,
        selections: Map<String, String>,
        persisted: Map<String, String>
    ): Result {
        val rows = mutableListOf<DynamicSelectorUi>()
        val resolved = linkedMapOf<String, String>()
        var levelItems = kinds
        var inheritedTitle: String? = null
        var currentUrl: String? = null
        var level = 0
        var steps = 0
        val safetyLimit = (countNodes(kinds) + 1).coerceAtLeast(16)

        while (levelItems.isNotEmpty() && steps++ < safetyLimit) {
            while (
                levelItems.size == 1 &&
                levelItems.first().targetUrl().isNullOrBlank() &&
                levelItems.first().children.orEmpty().isNotEmpty()
            ) {
                val container = levelItems.first()
                inheritedTitle = cleanTitle(container.title).ifBlank { inheritedTitle.orEmpty() }
                levelItems = container.children.orEmpty()
            }
            if (levelItems.isEmpty()) break

            // 与 legado 的 visibleItems 一致：纯装饰标题、无 URL 且无 children 的节点不进入分类行。
            val visibleItems = levelItems.filter {
                it.children.orEmpty().isNotEmpty() || !it.targetUrl().isNullOrBlank()
            }
            if (visibleItems.isEmpty()) break

            val id = "dynamic_level_$level"
            val selectedTitle = selections[id]
                ?.takeIf { saved -> visibleItems.any { it.title == saved } }
                ?: persisted[id]?.takeIf { saved -> visibleItems.any { it.title == saved } }
                ?: visibleItems.first().title
            resolved[id] = selectedTitle

            rows += DynamicSelectorUi(
                id = id,
                title = inferTitle(level, visibleItems, inheritedTitle),
                targets = visibleItems.map { kind ->
                    DiscoverySuiteWidgetTarget(
                        sourceUrl = sourceUrl,
                        tagUrl = kind.targetUrl().orEmpty(),
                        title = displayTitle(kind)
                    )
                }.toImmutableList(),
                selectedTitle = displayTitle(visibleItems.first { it.title == selectedTitle }),
                type = inferType(visibleItems)
            )

            val selected = visibleItems.firstOrNull { it.title == selectedTitle } ?: break
            selected.targetUrl()?.let { currentUrl = it }
            inheritedTitle = selected.title
            levelItems = selected.children.orEmpty()
            level++
        }

        return Result(rows, resolved, currentUrl, ExploreMode.TREE)
    }

    private fun buildSection(
        kinds: List<ExploreKind>,
        sourceUrl: String,
        selections: Map<String, String>,
        persisted: Map<String, String>
    ): Result {
        data class Section(val title: String, val sourceIndex: Int, val items: MutableList<IndexedValue<ExploreKind>>)

        val sections = mutableListOf<Section>()
        var current: Section? = null
        val looseItems = mutableListOf<IndexedValue<ExploreKind>>()

        kinds.forEachIndexed { index, kind ->
            if (kind.isSectionHeader()) {
                current = Section(cleanTitle(kind.title), index, mutableListOf()).also(sections::add)
            } else if (!kind.targetUrl().isNullOrBlank()) {
                val indexed = IndexedValue(index, kind)
                if (current != null) current!!.items += indexed else looseItems += indexed
            }
        }

        val usableSections = sections.filter { it.items.isNotEmpty() }
        if (usableSections.isEmpty()) return buildFlat(kinds, sourceUrl, selections, persisted)

        val rows = mutableListOf<DynamicSelectorUi>()
        val resolved = linkedMapOf<String, String>()
        var currentUrl: String? = null

        val sectionId = "dynamic_level_0"
        val selectedSectionTitle = selections[sectionId]
            ?.takeIf { title -> usableSections.any { it.title == title } }
            ?: persisted[sectionId]?.takeIf { title -> usableSections.any { it.title == title } }
            ?: usableSections.first().title
        resolved[sectionId] = selectedSectionTitle

        // 多个分组才显示频道/分组行；单个 Header 只是分类标题，不额外占一行。
        var categoryLevel = 0
        if (usableSections.size > 1) {
            rows += DynamicSelectorUi(
                id = sectionId,
                title = inferSectionTitle(usableSections.map { it.title }),
                targets = usableSections.map {
                    DiscoverySuiteWidgetTarget(sourceUrl, "", it.title)
                }.toImmutableList(),
                selectedTitle = selectedSectionTitle,
                type = DynamicSelectorUi.SelectorType.TagBar
            )
            categoryLevel = 1
        }

        val selectedSection = usableSections.first { it.title == selectedSectionTitle }
        val categoryItems = selectedSection.items.map { it.value }
        if (categoryItems.isNotEmpty()) {
            val categoryId = "dynamic_level_$categoryLevel"
            val selectedCategory = selections[categoryId]
                ?.let { title -> categoryItems.firstOrNull { it.title == title || displayTitle(it) == title } }
                ?: persisted[categoryId]?.let { title -> categoryItems.firstOrNull { it.title == title || displayTitle(it) == title } }
                ?: categoryItems.first()
            resolved[categoryId] = selectedCategory.title
            currentUrl = selectedCategory.targetUrl()
            rows += DynamicSelectorUi(
                id = categoryId,
                title = "分类",
                targets = categoryItems.map { kind ->
                    DiscoverySuiteWidgetTarget(sourceUrl, kind.targetUrl().orEmpty(), displayTitle(kind))
                }.toImmutableList(),
                selectedTitle = displayTitle(selectedCategory),
                type = DynamicSelectorUi.SelectorType.TagBar
            )
        }

        return Result(rows, resolved, currentUrl, ExploreMode.SECTION)
    }

    private fun buildFlat(
        kinds: List<ExploreKind>,
        sourceUrl: String,
        selections: Map<String, String>,
        persisted: Map<String, String>
    ): Result {
        val items = kinds.filter { !it.targetUrl().isNullOrBlank() }
        if (items.isEmpty()) return Result(emptyList(), emptyMap(), null, ExploreMode.FLAT)

        val id = "dynamic_level_0"
        val selected = selections[id]
            ?.let { title -> items.firstOrNull { it.title == title || displayTitle(it) == title } }
            ?: persisted[id]?.let { title -> items.firstOrNull { it.title == title || displayTitle(it) == title } }
            ?: items.first()

        val row = DynamicSelectorUi(
            id = id,
            title = inferTitle(0, items, null),
            targets = items.map { kind ->
                DiscoverySuiteWidgetTarget(sourceUrl, kind.targetUrl().orEmpty(), displayTitle(kind))
            }.toImmutableList(),
            selectedTitle = displayTitle(selected),
            type = inferType(items)
        )
        return Result(listOf(row), mapOf(id to selected.title), selected.targetUrl(), ExploreMode.FLAT)
    }

    private fun detectMode(kinds: List<ExploreKind>): ExploreMode {
        if (kinds.hasChildrenDeep()) return ExploreMode.TREE
        return if (kinds.any { it.isSectionHeader() }) ExploreMode.SECTION else ExploreMode.FLAT
    }

    private fun List<ExploreKind>.hasChildrenDeep(): Boolean =
        any { it.children.orEmpty().isNotEmpty() || it.children.orEmpty().hasChildrenDeep() }

    private fun ExploreKind.isSectionHeader(): Boolean {
        if (!targetUrl().isNullOrBlank() || !action.isNullOrBlank()) return false
        val text = cleanTitle(title)
        if (text.isBlank()) return false
        val style = style()
        if (style.layout_flexBasisPercent >= 0.95f ||
            (style.layout_flexGrow >= 1f && style.layout_flexBasisPercent < 0f)
        ) return true
        val compact = text.replace("\\s+".toRegex(), "")
        val decorations = compact.count {
            it == '◎' || it == '●' || it == '○' || it == '◆' || it == '◇' || it == '=' || it == '-'
        }
        return decorations >= 2 || compact.endsWith("分类") || compact.endsWith("排行") || compact.endsWith("排行榜")
    }

    private fun ExploreKind.targetUrl(): String? {
        val actionTarget = if (type == ExploreKind.Type.url) action else null
        return actionTarget?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
            ?: url?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
    }

    private fun inferTitle(level: Int, items: List<ExploreKind>, inheritedTitle: String?): String {
        val titles = items.map(::displayTitle)
        if (titles.any {
                it.contains("男频") || it.contains("女频") ||
                    it.contains("男生频道") || it.contains("女生频道")
            }) return "频道"
        if (titles.count { it in STATUS_TITLES } >= 2) return "状态"
        if (titles.count { it.isRankTitle() } >= 2) return "榜单"
        val inherited = cleanTitle(inheritedTitle.orEmpty())
        if (inherited.isNotBlank()) {
            when {
                inherited.contains("排行") || inherited.endsWith("榜") -> return inherited
                inherited in STANDARD_TITLES -> return inherited
            }
        }
        return if (level == 0 && items.all { it.targetUrl().isNullOrBlank() }) "分组" else "分类"
    }

    private fun inferSectionTitle(titles: List<String>): String =
        if (titles.any {
                it.contains("男频") || it.contains("女频") ||
                    it.contains("男生频道") || it.contains("女生频道")
            }) "频道" else "分组"

    private fun inferType(items: List<ExploreKind>): DynamicSelectorUi.SelectorType =
        if (items.map(::displayTitle).count { it.isRankTitle() } >= 2) {
            DynamicSelectorUi.SelectorType.RankButtons
        } else {
            DynamicSelectorUi.SelectorType.TagBar
        }

    private fun displayTitle(kind: ExploreKind): String = cleanTitle(kind.title).ifBlank { kind.type }

    private fun cleanTitle(title: String): String = title
        .replace(Regex("[\\[\\]【】?（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()

    private fun String.isRankTitle(): Boolean =
        this in RANK_TITLES || endsWith("榜") || contains("排行")

    private fun countNodes(kinds: List<ExploreKind>): Int =
        kinds.sumOf { 1 + countNodes(it.children.orEmpty()) }

    private val STATUS_TITLES = setOf("全部", "连载", "完结", "新书", "短篇", "长篇", "免费", "付费")
    private val RANK_TITLES = setOf("推荐", "评分", "热门", "周榜", "月榜", "总榜", "日榜", "本周", "本月", "本日")
    private val STANDARD_TITLES = setOf("分类", "频道", "状态", "榜单", "标签", "类型")
}
