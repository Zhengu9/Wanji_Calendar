# ChoDemoApp

## 项目简介
CalendarAgentApp 是一个基于 Android 和 Compose Multiplatform 的日历应用示例。

## 开发环境配置

为了保护敏感信息，本项目已将配置文件排除在 Git 版本控制之外。在编译和运行项目之前，请按照以下步骤在**项目根目录**下创建必要的配置文件。

### 1. 配置 `local.properties`

创建 `local.properties` 文件，并指定你的 Android SDK 路径：

```properties
# 替换为你自己的 SDK 路径
sdk.dir=/Users/your_username/Library/Android/sdk
```

### 2. 配置 `.env`

创建 `.env` 文件，并在其中添加你的 API Key：

```properties
# 替换为你的 Kimi API Key
kimiApiKey=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

### 3. 配置 `后端.env`

创建 `后端.env` 文件，同样填入 API Key：

```properties
# 替换为你的 Kimi API Key
kimiapi=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> **注意**：以上文件均包含敏感信息，请勿将其提交到公共代码仓库。

## 运行项目

完成上述配置后，请在 Android Studio 中点击 "Sync Project with Gradle Files"，然后即可运行 App。
