# LiteBrowser - 精简浏览器

基于 Mozilla GeckoView 内核的 Android 精简浏览器，支持多标签、书签、历史记录等功能。

## 功能特性

- 🔗 **网页浏览** - 基于 GeckoView 内核，完整 Web 标准支持
- 📑 **多标签页** - 创建、切换、关闭标签页
- 🔙 **导航控制** - 前进/后退/刷新
- 🔍 **智能地址栏** - 自动识别 URL 和搜索关键词
- 🔒 **安全指示** - HTTPS 网站显示安全锁图标
- ⭐ **书签管理** - 添加/移除书签
- 📜 **历史记录** - 自动记录浏览历史
- 🛡️ **跟踪保护** - 内置 ContentBlocking 内容拦截
- 📱 **兼容性** - 支持 Android 5.0 (API 21) 及以上

## 编译方式

### 方式一：GitHub Actions 自动编译（推荐）

1. Fork 本仓库或推送到你的 GitHub 仓库
2. 进入仓库的 **Actions** 标签页
3. 每次 push 到 `main` 分支会自动触发编译
4. 编译完成后，在 Actions 页面下载 APK 文件

### 方式二：本地编译

#### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 17
- Android SDK API 34

#### 编译步骤

```bash
# 克隆仓库
git clone https://github.com/yourusername/LiteBrowser.git
cd LiteBrowser

# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本
./gradlew assembleRelease
```

编译完成后，APK 文件位于：
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

#### 使用 Android Studio

1. 打开 Android Studio
2. 选择 **Open** → 选择 `LiteBrowser` 目录
3. 等待 Gradle 同步完成
4. 连接设备或启动模拟器
5. 点击 **Run** 按钮

## 项目结构

```
LiteBrowser/
├── app/
│   ├── src/main/
│   │   ├── java/com/litebrowser/app/
│   │   │   ├── MainActivity.kt       # 主界面
│   │   │   ├── TabManager.kt        # 标签管理
│   │   │   ├── BrowserDatabase.kt   # 数据库
│   │   │   ├── BookmarkActivity.kt  # 书签页面
│   │   │   └── HistoryActivity.kt   # 历史记录页面
│   │   └── res/                     # 布局和资源文件
│   └── build.gradle.kts             # 应用构建配置
├── .github/workflows/build.yml      # GitHub Actions 工作流
└── build.gradle.kts                 # 项目构建配置
```

## 技术栈

- **Kotlin** - 编程语言
- **GeckoView** - Mozilla 浏览器内核
- **AndroidX** - Android 支持库
- **Material Design** - UI 组件
- **SQLite** - 本地数据存储
- **Coroutines** - 异步编程

## 许可证

本项目采用 Mozilla Public License v2.0 许可证。
