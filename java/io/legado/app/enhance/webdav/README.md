# WebDAV Enhance Module (WebDAV 同步增强)

此模块扩展了主项目的 WebDAV 功能，支持书籍实体的云同步（云文档体验）。

## 核心功能

*   **增量导出**:
    *   逻辑位于 `WebDavEnhance.kt`。
    *   导出前会通过 `getWebDavFile()` 检查远程文件。
    *   **比对逻辑**: 只有当远程文件不存在，或文件大小与本地不一致时，才会触发上传。
*   **批量导入**:
    *   扫描 WebDAV 的 `books/` 目录，将本地书架不存在的书籍自动下载并静默导入。
*   **通知系统**:
    *   导出/导入完成后，通过 `NotificationManager` 发送详细的成功/跳过/失败统计。

## 故障排查

*   **识别失败**: 如果明明有文件却还是重复上传，请检查 `WebDavEnhance.getHttpUrl` 是否正确处理了带空格或特殊字符的 URL 编码。
