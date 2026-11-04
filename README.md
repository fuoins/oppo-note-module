# OPPO便签模块

面向 OPPO 便签（`com.coloros.note`）的 LSPosed 模块，由 **GitHub Actions** 编译出包。

> 模块基于**传统 XposedBridge API 82**，兼容所有 LSPosed 版本，不依赖会随版本变化的 libxposed API。

## 目标应用

| 项 | 值 |
| --- | --- |
| 包名 | `com.coloros.note` |
| 已验证版本 | 16.7.2（versionCode 160070002） |
| 入口 Application | `com.nearme.note.MyApplication` |
| 主 Activity | `com.nearme.note.main.MainActivity` |
| 设置页 | `com.nearme.note.setting.SettingsActivity` → `SettingFragment` |
| 待办列表 | `com.nearme.note.activity.list.TodoAdapter` |

## 功能

### 已实现

- **模块无桌面图标**：安装后不在桌面显示，避免误触；
- **设置页入口**：在便签「设置」列表底部插入「模块设置」，点击进入模块界面；
- **运行日志**：便签进程的日志经 ContentProvider 跨进程落盘，在「模块设置 → 运行日志」直接查看，
  支持复制 / 分享 / 清空，无需连电脑看 logcat；
- **「添加」面板入口**：笔记编辑页点「＋」弹出的添加面板里插入「批量与排版」，
  注入点是 `COUIPopupListWindow.setItemList(List)`，复用原生菜单样式；
- **批量与排版**：
  - 字段可自由组合：日历、任意文字（完成 / 忘记 / …）；
  - 日历支持 Java 日期格式，内置 `11.04`、`11月4日`、`2026年11月4日`、`11月4日 周三`、`11.04 14:30` 等预设，也可自定义；
  - 间隔方式：每天、隔 N 天、每周固定星期几；
  - 批量条数可设置（1–2000）；
  - 排版：字段间用 N 个空格，或换行（0 = 每字段一行）；
  - 列对齐：按显示宽度补齐空格，中文按 2 个半角宽度计算；
  - 生成结果可直接发送到便签新建笔记，或复制到剪贴板；
  - **清理过期行**：按当前日期格式识别行首日期，删除早于今天的整行（粘贴文本 → 清理 → 复制回去）。

### 适配中

- 待办勾选框右置、一行多个（编辑页待办块由 WebView 富文本渲染，需按真实 DOM 写 CSS）；
- 待办每日刷新（恢复勾选状态）；
- 首页列表样式增强。

编辑页正文是 `com.oplus.richtext` 的 WebView 富文本，类名混淆、资源名被改写，
无法靠静态分析确定待办块的 DOM 结构。模块内置了**结构探测**（`web_probe`）：
页面加载完成后注入一段**只读**脚本，把元素统计与待办候选节点的 `outerHTML` 写入运行日志。
拿这份结构即可精确编写 CSS 规则，不需要靠猜。

## 安装与生效

1. 安装 APK（模块无桌面图标，属正常现象）；
2. LSPosed 中启用「OPPO便签模块」，作用域勾选「便签」；
3. 强行停止便签，重新打开；
4. 便签「设置」→ 最下方「模块设置」；
5. 验证：进入「模块设置 → 运行日志」，应能看到
   ```
   I loadPackage: process=com.coloros.note
   I Application ready, installing features
   I installed [add_menu]
   I add_menu: setItemList hooked=1
   I installed [web_probe]
   I web_probe: hooked XxxWebViewClient#onPageFinished
   ```

> 修改开关后需强行停止便签进程再打开才生效。

## 目录结构

```
app/src/main/java/com/fuoins/opponote/
├── ModuleEntry.kt              # IXposedHookLoadPackage 入口
├── core/
│   ├── Feature.kt              # 功能接口
│   ├── FeatureRegistry.kt      # 功能注册表
│   ├── HookContext.kt          # 反射查找 + Hook 封装
│   ├── Prefs.kt                # 宿主侧配置读取
│   ├── ConfigProvider.kt       # 模块侧配置暴露（ContentProvider）
│   ├── BatchEngine.kt          # 批量生成与列对齐算法
│   └── ExpiredCleaner.kt       # 过期行清理
├── hooks/
│   ├── SettingsEntryFeature.kt # 设置页入口注入
│   └── DiagnosticsFeature.kt   # 自检日志
├── ui/
│   ├── SettingsActivity.kt     # 模块设置
│   └── BatchFormatActivity.kt  # 批量与排版
└── util/XLog.kt

xposed-api-stub/                # 传统 Xposed API 编译期桩（不打包）
```

## 添加一个新功能

```kotlin
object MyFeature : Feature {
    override val id = "my_feature"
    override val title = "我的功能"
    override val summary = "说明"
    override val enabledByDefault = false

    override fun install(ctx: HookContext) {
        ctx.hookMethod(
            "com.nearme.note.xxx.Target",
            "methodName",
            String::class.java,
            after = { param: XC_MethodHook.MethodHookParam ->
                // 处理
            },
        )
    }
}
```

注册到 `FeatureRegistry.all` 即可，设置页开关会自动出现（需自行在 SettingsActivity 添加入口）。

## 构建

- 推送到 `main` / 手动触发：生成 debug + release APK 到 Actions Artifacts；
- 打 `v*` 标签：自动创建 Release 并上传 release APK；
- Release 使用仓库 Secrets 中的固定签名（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），未配置时回退 debug 签名。

```bash
./gradlew assembleRelease
```

## 说明

- 所有 Hook 均在 `runCatching` 中执行，失败只记录日志，不影响便签原有流程；
- 模块只处理本机文本与显示，不修改便签数据库结构，不干预云同步协议。
