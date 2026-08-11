# Explore Module (发现页增强)

此模块实现了 Legado 的“发现页套件 (DiscoverySuite)”功能，支持瀑布流布局、自动类目解析和自定义搜索。

## 主要组件

*   **`vm/ExploreViewModelEnhance.kt`**:
    *   作为上游 `ExploreViewModel` 的代理，处理套件切换、数据加载。
    *   **核心逻辑**: `rebuildSelectors` 会递归扫描书源的 `exploreKinds`，并根据嵌套层级自动生成多级筛选菜单（如：频道 -> 分类 -> 榜单）。
*   **`builder/`**:
    *   `ExploreTreeBuilder`: 将扁平的书源分类转换为树状结构。
    *   `ExploreFilterBuilder`: 将树状结构映射为可交互的 UI 过滤器。
*   **`ui/ExploreLayoutEngine.kt`**:
    *   实现了瀑布流模式下的智能布局算法，确保封面和书籍信息排版美观。
*   **`screen/DiscoverySuiteScreen.kt`**:
    *   瀑布流主界面，集成了多级筛选、书籍网格和搜索结果展示。

## 开发注意事项

*   **书源适配**: 如果某个书源的分类无法正确显示，请检查 `ExploreModeDetector` 逻辑，确保它能识别该源的 HTML 结构特点。
*   **性能**: 筛选树的解析在 `IO` 线程执行，避免在大书源下阻塞 UI。
