# OPPO便签模块

面向 OPPO 便签（`com.coloros.note`）的 LSPosed 模块，基于 [libxposed API](https://github.com/LSPosed/LSPosed) 构建，全程由 **GitHub Actions** 编译出包。

> 当前版本只包含**模块框架 + 自检功能**（仅写日志，不修改便签行为）。具体 Hook 功能按需求在 `FeatureRegistry` 中逐项添加。

## 目标应用

| 项 | 值 |
| --- | --- |
| 包名 | `com.coloros.note` |
| 已验证版本 | 16.7.2（versionCode 160070002） |
| 入口 Application | `com.nearme.note.MyApplication` |
| 主 Activity | `com.nearme.note.main.MainActivity` |
| 主要代码包 | `com.nearme.note` / `com.oplus.note` |

## 目录结构

```
app/src/main/java/com/fuoins/opponote/
├── ModuleEntry.kt              # 模块入口（assets/xposed_init 指定）
├── core/
│   ├── Feature.kt              # 功能接口
│   ├── FeatureRegistry.kt      # 功能注册表（新增功能只需改这里）
│   ├── HookContext.kt          # 类/方法查找与 Hook 便捷封装
│   └── Prefs.kt                # 配置读写（与模块 App 共用）
├── hooks/
│   └── DiagnosticsFeature.kt   # 自检：仅记录日志
├── ui/MainActivity.kt          # 模块配置界面
└── util/XLog.kt                # 统一日志（TAG = OPPONote）
```

## 添加一个新功能

1. 在 `hooks/` 下新建对象实现 `Feature`：

```kotlin
object NoUpdateDialogFeature : Feature {
    override val id = "no_update_dialog"
    override val title = "屏蔽更新弹窗"
    override val summary = "拦截强制升级提示"
    override val enabledByDefault = false

    override fun install(ctx: HookContext) {
        ctx.hookMethod("com.nearme.note.xxx.UpdateHelper", "showDialog") { chain ->
            XLog.i("update dialog blocked")
            null // 不调用 chain.proceed() 即拦截
        }
    }
}
```

2. 注册到 `FeatureRegistry.all`，UI 开关会自动出现。

## 待定功能（按需实现）

框架已就绪，以下方向可按需求逐项实现，每项独立成一个 `Feature`：

- 去广告 / 去推广位（开屏、信息流、Banner）
- 去更新提示（强制升级弹窗）
- 解除导出 / 分享格式限制

> 新增功能请先在真机确认目标类与方法签名，再编写 Hook；`XLog` + LSPosed 日志可直接观察调用链。

## 构建

### GitHub Actions（推荐）

推送到 `main` 或手动触发 `Build` workflow，产物在 Actions 的 Artifacts 中。
打 `v*` 标签会自动创建 Release：

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Release 使用仓库 Secrets 中的固定签名（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），
未配置时自动回退到 debug 签名。

### 本地

```bash
./gradlew assembleDebug
```

## 安装与生效

1. 安装 APK；
2. LSPosed 中启用「OPPO便签模块」，作用域勾选「便签」；
3. 强行停止并重新打开便签；
4. 在 LSPosed 日志或 `adb logcat -s OPPONote` 中确认：
   ```
   module loaded: process=com.coloros.note, api=102
   diagnostics: MainActivity.onCreate reached, hook chain works
   ```

## 说明

- 模块运行时依赖框架提供的 `io.github.libxposed:api`，该依赖为 `compileOnly`，不会打包进 APK。
- 修改功能开关后需重启便签进程才能生效。
