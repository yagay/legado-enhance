# Legado Enhance (增强模块)

此项目作为 `legado-with-MD3` 的子模块，专门存放与上游源码存在差异的增强功能、自定义 UI 逻辑及相关资源。

## 目录结构说明

### 1. 核心逻辑 (`java/io/legado/app/enhance`)

*   **`EnhanceModule.kt`**: 增强功能的依赖注入 (Koin) 模块。
*   **`explore/`**: 发现页增强功能。
    *   `builder/`: 自动构建发现页筛选树和过滤器的逻辑。
    *   `model/`: 发现页瀑布流套件 (DiscoverySuite) 的数据模型。
    *   `screen/`: 发现页增强层的 Compose 界面。
    *   `ui/`: 瀑布流布局引擎和自定义 UI 组件。
    *   `vm/`: 发现页增强层的业务逻辑实现。
*   **`settingssearch/`**: 设置项搜索与定位增强。
    *   `GeneratedSettingCatalog.kt`: 由脚本自动生成的设置项索引。
    *   `GeneratedSettingLocator.kt`: 设置项索引匹配逻辑。
*   **`ui/`**: 通用 UI 委托。
    *   `MyViewModelEnhance.kt`: “我的”页面业务逻辑委托（如搜索注册、一键导出等）。
    *   `SettingScrollEnhance.kt`: 设置项自动滚动定位动画逻辑。
*   **`webdav/`**: WebDAV 增强。
    *   `WebDavEnhance.kt`: 增强型 WebDAV 批量导入、导出和远程校验逻辑。
*   **`model/`**: 持久化设置模型。
    *   `CustomSettings.kt`: 存放所有自定义功能开关的数据类。

### 2. 资源文件 (`res/`)

*   **`values*/strings_custom.xml`**: 存放所有增强功能专用的多语言字符串，与上游 `strings.xml` 物理隔离。

### 3. 构建脚本

*   **`setting-search.gradle`**: 自动化构建脚本。在编译前扫描所有设置页的 `*SettingItem`，并生成 `GeneratedSettingCatalog.kt` 索引文件，以支持设置项搜索。

## 如何修改

1.  **添加新功能**: 在 `enhance` 下建立对应文件夹，逻辑通过 `EnhanceModule` 注入。
2.  **修改字符串**: 始终修改 `res/strings_custom.xml`，不要动主项目的 `strings.xml`。
3.  **同步**:
    *   在子模块目录修改后，先在此处提交推送。
    *   在主项目中执行 `git submodule update --remote` 同步。
