# Magnet Downloader

一个基于 Android 的 Magnet 链接下载器应用，使用 Jetpack Compose 构建界面，基于 libtorrent4j 实现 BitTorrent 协议。

## 功能特性

- **Magnet 链接下载**：支持通过 Magnet URI 下载 BitTorrent 资源
- **链接识别**：从其他应用分享或点击 Magnet 链接时自动打开本应用
- **下载管理**：查看、暂停、恢复和删除下载任务
- **实时进度**：显示下载速度、进度百分比、Peer 数量等信息
- **本地存储**：下载文件保存到手机内部存储的 Download/MagnetDownloader 目录
- **现代 UI**：使用 Jetpack Compose 构建的 Material Design 3 界面
- **后台下载**：支持后台下载服务，即使应用退出也能继续下载

## 技术栈

- **语言**：Kotlin
- **UI 框架**：Jetpack Compose + Material Design 3
- **架构**：MVVM + Repository 模式
- **依赖注入**：Hilt
- **数据存储**：Room 数据库
- **BitTorrent 引擎**：libtorrent4j
- **后台服务**：Foreground Service

## 构建要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 11 或更高版本
- Android SDK 34
- Gradle 8.4

## 构建步骤

```bash
./gradlew assembleDebug
```

## 权限说明

- **INTERNET**：网络访问，用于 BitTorrent 下载
- **ACCESS_NETWORK_STATE**：检查网络状态
- **WRITE_EXTERNAL_STORAGE** (Android 10 及以下)：写入下载文件
- **FOREGROUND_SERVICE**：后台下载服务
- **POST_NOTIFICATIONS** (Android 13+)：下载进度通知

## 下载位置

下载的文件保存在：`内部存储/Download/MagnetDownloader/`
