# Legado Enhance Beta

`Beta` 是供 `yagay/legado` XML/RecyclerView 主项目使用的轻量增强核心。

## 设计边界

- 模块只维护模型、策略和状态，不绘制发现页或书籍列表。
- 主项目继续负责主题、XML、Adapter、数据库、WebBook、WebDAV、分页和点击行为。
- 瀑布流继续使用主项目的 `item_search.xml`；模块只返回封面尺寸和简介行数。
- 模块不反向依赖 `io.legado.app.*`，方便主项目合并上游更新。

## 接入

把本分支作为主项目的 `modules/legado-enhance`：

```groovy
// settings.gradle
include ':modules:legado-enhance'

// app/build.gradle
implementation project(':modules:legado-enhance')
```

## 当前核心

- `ExplorePlanner`：发现分类模式判断、树构建和扁平化。
- `ExploreResultLayoutPolicy`：主项目原生列表与瀑布流的测量参数。

历史 MD3/Compose 实现在 `MD3` 分支维护，不进入 `Beta` 的编译和发布内容。
