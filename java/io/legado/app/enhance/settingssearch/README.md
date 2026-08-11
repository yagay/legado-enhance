# Settings Search Module (设置搜索增强)

此模块通过自动化脚本支持对 Legado 繁杂设置项的全局搜索，并支持跳转时的自动滚动和高亮。

## 工作流程

1.  **扫描 (Build Time)**: Gradle 运行 `setting-search.gradle` 脚本。
2.  **生成**: 脚本解析所有 `*ConfigScreen.kt` 文件，提取 `SettingItem` 的标题（Resource ID）和所属的分组索引。
3.  **索引**: 生成 `GeneratedSettingCatalog.kt` 包含所有项的元数据。
4.  **匹配 (Runtime)**: 当用户在“我的”页面搜索并点击时，`GeneratedSettingLocator` 会根据标题找到目标项。
5.  **滚动**: 页面通过 `SettingScrollEnhance` 自动计算偏移量并执行平滑滚动动画。

## 如何维护

*   **索引更新**: 每次在上游 Screen 中添加、删除或移动 `SettingItem` 后，执行 `gradle generateSettingSearchCatalog` 或直接进行编译。
*   **高亮颜色**: 在 `SettingItem.kt` 组件中修改，当前默认使用主题色的淡色背景。
