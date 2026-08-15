package io.legado.app.enhance.explore.builder

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode
import io.legado.app.utils.GSON

/**
 * 现代发现页分类引擎。
 *
 * 逻辑对齐 yagay/legado:master 的现代布局：
 * 1. 优先从 exploreKindsJson() 读取原始 children，避免扁平化后再猜层级；
 * 2. 只有原始 JSON 没有真正树结构时才回退到 exploreKinds()；
 * 3. 对“男/女频道 -> 分类 -> 状态/榜单”的二维平铺结构恢复成稳定的三级树；
 * 4. 最后识别 TREE / SECTION / FLAT，供 MD3 现代布局使用。
 *
 * 不依赖具体书源名称或域名，保持增强模块可独立维护。
 */
object ModernExploreClassificationEngine {

    data class Result(
        val kinds: List<ExploreKind>,
        val mode: ExploreMode
    )

    fun classify(flatKinds: List<ExploreKind>, rawJson: String): Result {
        val parsedTree = parseRawTree(rawJson)
        val base = parsedTree.takeIf { it.hasChildrenDeep() } ?: flatKinds
        val rebuilt = buildSectionMatrixTree(base) ?: base
        return Result(rebuilt, detectMode(rebuilt))
    }

    private fun parseRawTree(json: String): List<ExploreKind> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            GSON.fromJson(json, JsonArray::class.java).mapNotNull(::parseNode)
        }.getOrDefault(emptyList())
    }

    private fun parseNode(element: JsonElement): ExploreKind? {
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        val kind = GSON.fromJson(obj, ExploreKind::class.java) ?: return null
        val children = obj.get("children")
            ?.takeIf(JsonElement::isJsonArray)
            ?.asJsonArray
            ?.mapNotNull(::parseNode)
            .orEmpty()
        return if (children.isEmpty()) kind else kind.copy(children = children)
    }

    private fun detectMode(kinds: List<ExploreKind>): ExploreMode {
        if (kinds.hasChildrenDeep()) return ExploreMode.TREE
        return if (kinds.any(::isSectionHeader)) ExploreMode.SECTION else ExploreMode.FLAT
    }

    private fun List<ExploreKind>.hasChildrenDeep(): Boolean =
        any { !it.children.isNullOrEmpty() || it.children.orEmpty().hasChildrenDeep() }

    /**
     * 对齐参考源码 buildDiscoverSectionMatrixTree：
     *
     * 男频
     *   玄幻
     *     推荐 / 完结 / 连载 ...
     *     热门 / 完结 / 连载 ...
     * 女频
     *   现言
     *     推荐 / 完结 / 连载 ...
     *
     * 这类书源实际上用样式和排列表示二维矩阵，没有 children；现代布局在这里恢复层级。
     */
    private fun buildSectionMatrixTree(kinds: List<ExploreKind>): List<ExploreKind>? {
        if (kinds.any { !it.children.isNullOrEmpty() }) return null

        val channels = mutableListOf<MatrixChannel>()
        var currentChannel: MatrixChannel? = null
        var currentCategory: MatrixCategory? = null

        kinds.forEach { kind ->
            val target = targetUrl(kind)
            val isHeader = target.isNullOrBlank() &&
                kind.action.isNullOrBlank() &&
                isFullWidth(kind) &&
                cleanTitle(kind.title).isNotBlank()

            if (isHeader) {
                val title = cleanTitle(kind.title)
                if (isChannelTitle(title)) {
                    currentChannel = MatrixChannel(kind, mutableListOf()).also(channels::add)
                    currentCategory = null
                } else if (currentChannel != null) {
                    currentCategory = MatrixCategory(kind, mutableListOf()).also {
                        currentChannel!!.categories += it
                    }
                }
                return@forEach
            }

            if (!target.isNullOrBlank() && currentCategory != null) {
                currentCategory!!.leaves += kind
            }
        }

        if (channels.size < 2 || channels.any { it.categories.isEmpty() }) return null
        val rebuilt = channels.mapNotNull { channel ->
            val categories = channel.categories.mapNotNull(::buildMatrixCategory)
            if (categories.isEmpty()) null else channel.header.copy(
                url = null,
                action = null,
                children = categories
            )
        }
        return rebuilt.takeIf { it.size >= 2 }
    }

    private fun buildMatrixCategory(category: MatrixCategory): ExploreKind? {
        val rankOrder = mutableListOf<String>()
        val statusOrder = mutableListOf<String>()
        val combinations = linkedMapOf<String, LinkedHashMap<String, ExploreKind>>()
        var currentRank: String? = null

        category.leaves.forEach { leaf ->
            val title = cleanTitle(leaf.title)
            if (isRankTitle(title)) {
                currentRank = title
                if (title !in rankOrder) rankOrder += title
            }
            val rank = currentRank ?: return@forEach
            val status = if (isRankTitle(title)) "全部" else title
            if (status.isBlank()) return@forEach
            if (status !in statusOrder) statusOrder += status
            combinations.getOrPut(status) { linkedMapOf() }[rank] = leaf
        }

        if (rankOrder.size < 2 || statusOrder.size < 2) return null
        if (statusOrder.any { combinations[it].orEmpty().size < 2 }) return null

        val statusNodes = statusOrder.mapNotNull { status ->
            val rankLeaves = rankOrder.mapNotNull { rank ->
                combinations[status]?.get(rank)?.copy(title = rank, children = null)
            }
            if (rankLeaves.isEmpty()) null else ExploreKind(
                title = status,
                children = rankLeaves
            )
        }
        if (statusNodes.isEmpty()) return null
        return category.header.copy(url = null, action = null, children = statusNodes)
    }

    private fun isSectionHeader(kind: ExploreKind): Boolean {
        if (!targetUrl(kind).isNullOrBlank() || !kind.action.isNullOrBlank()) return false
        val title = cleanTitle(kind.title)
        if (title.isBlank()) return false
        if (isFullWidth(kind)) return true
        val compact = title.replace("\\s+".toRegex(), "")
        val decorations = compact.count {
            it == '◎' || it == '●' || it == '○' || it == '◆' || it == '◇' || it == '=' || it == '-'
        }
        return decorations >= 2 || compact.endsWith("分类") ||
            compact.endsWith("排行") || compact.endsWith("排行榜")
    }

    private fun targetUrl(kind: ExploreKind): String? =
        kind.action?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }
            ?: kind.url?.trim()?.takeIf { it.isNotBlank() && !it.equals("null", true) }

    private fun isFullWidth(kind: ExploreKind): Boolean {
        val style = kind.style()
        return style.layout_flexBasisPercent >= 0.95f ||
            (style.layout_flexGrow >= 1f && style.layout_flexBasisPercent < 0f)
    }

    private fun isChannelTitle(title: String): Boolean =
        title.contains("男频") || title.contains("女频") ||
            title.contains("男生频道") || title.contains("女生频道")

    private fun isRankTitle(title: String): Boolean =
        title in RANK_TITLES || title.endsWith("榜") || title.contains("排行")

    private fun cleanTitle(title: String): String = title
        .replace(Regex("[\\[\\]【】?（）<>《》]"), "")
        .replace(Regex("[\\p{So}\\p{Sk}]+"), "")
        .replace(Regex("[༺༻ˇ»«`´ʚɞ]+"), "")
        .trim()

    private data class MatrixChannel(
        val header: ExploreKind,
        val categories: MutableList<MatrixCategory>
    )

    private data class MatrixCategory(
        val header: ExploreKind,
        val leaves: MutableList<ExploreKind>
    )

    private val RANK_TITLES = setOf(
        "推荐", "评分", "热门", "周榜", "月榜", "总榜", "日榜", "本周", "本月", "本日"
    )
}
