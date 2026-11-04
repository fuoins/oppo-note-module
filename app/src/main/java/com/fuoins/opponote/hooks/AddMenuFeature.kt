package com.fuoins.opponote.hooks

import android.content.Context
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupWindow
import com.fuoins.opponote.ModuleEntry
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Method

/**
 * 在笔记编辑页「＋」插入面板（底部网格弹窗）中加入「批量与排版」。
 *
 * 真实调用链（v16.7.2 反编译确认）：
 *
 *   工具栏「＋」 → WVNoteViewEditFragment$onActivityCreated$2.onQuickInput()
 *   → addQuickInput() → RichTextFormatOptionMenuManager.addQuickInput(block)
 *   → enterSlash("toolbar_click") → WebView 回报光标矩形
 *   → showMenu() → showPopWindow()
 *     ├─ getMatchMenuList("")         // 完整插入列表（authenticAllMenuDataList）
 *     ├─ new RichTextFormatOptionMenu(context)
 *     ├─ popup.setData(list)         ← 注入点 ①：往列表尾部追加自定义项
 *     └─ calculatePositionAndShow(...)
 *
 *   点击项 → MenuAdapter → OnItemClickListener
 *   → handleMatchMenu(data, web)     ← 注入点 ②：识别自定义 type 并打开模块界面
 *
 * 旧实现注入的 ExtraOptionMenu / COUIPopupListWindow 在本版本中已被弃用
 * （全 APK 零引用，是旧编辑器的死代码），因此之前「添加」面板里看不到入口。
 *
 * v1.3.2：面板注入在部分机型上不显示，增加更可靠的第二入口——
 * 编辑页右上角「三个点」菜单（WVNoteViewEditFragment#onCreateOptionsMenu /
 * #onOptionsItemSelected），MenuItem id 用 0x7F0A0001 避开宿主 0x7F09 段。
 *
 * 面板类型判别：插入面板列表包含 album / take_photo / paint_entry / title_table /
 * title_file / scan_document / ocr 等 type，而「/」斜杠格式菜单只有 title /
 * subtitle / bullet / numbered 等，据此只对插入面板注入。
 */
class AddMenuFeature : Feature {

    override val id = "add_menu"
    override val title = "批量排版入口"
    override val summary = "编辑页「＋」面板与右上角三点菜单加入「批量与排版」"
    override val enabledByDefault = true

    /** 自定义项的 type 标识，避开原生类型 */
    private val customType = "batch_format"

    /** 三点菜单里自定义项的 MenuItem id（宿主会重写动态 add 项的 id，以标题匹配为准） */
    private val customMenuId = 0x7F0A0001

    private var launchedAt = 0L

    /** 诊断日志限流：其他菜单项点击 */
    private var clickLogAt = 0L

    /** 只在第一次跳过注入时记日志，避免斜杠菜单刷屏 */
    private var skipLogged = false

    override fun install(ctx: HookContext) {
        // 注入点 ①：面板数据构建完成、交给弹窗前，追加自定义项
        val setDataHooked = ctx.hookAllMethods(
            POPUP_CLASS,
            "setData",
            before = { param: XC_MethodHook.MethodHookParam -> injectItem(param, ctx) },
        )
        XLog.i("add_menu: RichTextFormatOptionMenu#setData hooked=$setDataHooked")

        // 注入点 ②：点击分发处识别自定义项
        val clickHooked = ctx.hookAllMethods(
            MANAGER_CLASS,
            "handleMatchMenu",
            before = { param: XC_MethodHook.MethodHookParam -> handleClick(param) },
        )
        XLog.i("add_menu: handleMatchMenu hooked=$clickHooked")

        // 诊断：确认「＋」按钮确实走了这条链
        ctx.hookAllMethods(
            FRAGMENT_CLASS,
            "addQuickInput",
            before = { XLog.i("add_menu: 「＋」被点击 (addQuickInput)") },
        )

        // 注入点 ③：编辑页右上角「三个点」菜单追加「批量与排版」
        // 注入点 ④：拦截三点菜单里自定义项的点击
        // 覆盖普通笔记 / 快捷便签 / 涂鸦便签三种编辑页 Fragment
        for (cls in MENU_FRAGMENT_CLASSES) {
            val menuHooked = ctx.hookAllMethods(
                cls,
                "onCreateOptionsMenu",
                after = { param: XC_MethodHook.MethodHookParam -> addThreeDotEntry(param) },
            )
            val itemHooked = ctx.hookAllMethods(
                cls,
                "onOptionsItemSelected",
                before = { param: XC_MethodHook.MethodHookParam -> interceptMenuClick(param) },
            )
            XLog.i("add_menu: 三点菜单 hook $cls menu=$menuHooked item=$itemHooked")
        }

        // 注入点 ⑤：Activity 层兜底拦截——宿主可能先于 Fragment 消费菜单点击
        // （v1.3.2 现象：点击「批量与排版」后退回主页且 Fragment 层无日志，
        //   说明点击未分发到 Fragment.onOptionsItemSelected）
        val faHooked = ctx.hookAllMethods(
            "androidx.fragment.app.FragmentActivity",
            "onMenuItemSelected",
            before = { param: XC_MethodHook.MethodHookParam -> interceptActivityClick(param, "FragmentActivity#onMenuItemSelected") },
        )
        XLog.i("add_menu: FragmentActivity#onMenuItemSelected hooked=$faHooked")

        val actHooked = ctx.hookAllMethods(
            "android.app.Activity",
            "onOptionsItemSelected",
            before = { param: XC_MethodHook.MethodHookParam -> interceptActivityClick(param, "Activity#onOptionsItemSelected") },
        )
        XLog.i("add_menu: Activity#onOptionsItemSelected hooked=$actHooked")

        // 崩溃捕获：未捕获异常写本地文件（进程被杀时跨进程日志会丢失，
        // 用户反馈的「退回主页」+ 进程 3 秒重启 4 次疑似崩溃重启，需要堆栈）
        installCrashCapture(ctx)
    }

    /** 包装默认未捕获异常处理器，把崩溃堆栈写到宿主 filesDir */
    private fun installCrashCapture(ctx: HookContext) {
        runCatching {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                runCatching {
                    val file = java.io.File(ctx.app.filesDir, "opponote_crash.log")
                    file.appendText(
                        "\n=== ${java.util.Date()} ===\nthread=${t.name}\n" +
                            android.util.Log.getStackTraceString(e) + "\n",
                    )
                }
                prev?.uncaughtException(t, e)
            }
            XLog.i("add_menu: 崩溃捕获已安装 -> ${ctx.app.filesDir}/opponote_crash.log")
        }.onFailure { XLog.e("add_menu: 崩溃捕获安装失败", it) }
    }

    /** 在弹窗 setData 时追加自定义项 */
    private fun injectItem(param: XC_MethodHook.MethodHookParam, ctx: HookContext) {
        val list = param.args.getOrNull(0) as? MutableList<Any?> ?: return

        // 重建后可能已经加过，避免重复
        if (list.any { readType(it) == customType }) return

        // 只对「添加」插入面板注入，斜杠格式菜单不动
        if (!isInsertPanelList(list)) return

        val item = buildItem(ctx.classLoader) ?: return
        runCatching { list.add(0, item) }
            .onSuccess { XLog.i("add_menu: 已注入「批量与排版」到面板第一项，面板共 ${list.size} 项") }
            .onFailure { XLog.e("add_menu: 注入失败", it) }
    }

    /** 通过列表中的 type 判断是否为「添加」插入面板 */
    private fun isInsertPanelList(list: List<Any?>): Boolean {
        val insertTypes = setOf(
            "album", "take_photo", "take_video", "paint_entry",
            "title_table", "title_file", "scan_document", "ocr",
        )
        val found = list.any { readType(it) in insertTypes }
        if (!found && !skipLogged) {
            val types = list.mapNotNull { readType(it) }
            XLog.i("add_menu: 非插入面板列表，跳过（类型: $types）")
            skipLogged = true
        }
        return found
    }

    /** 反射构造 com.nearme.note.activity.richedit.entity.RichFormatMenuData */
    private fun buildItem(classLoader: ClassLoader): Any? = runCatching {
        val cls = Class.forName(MENU_DATA_CLASS, false, classLoader)
        val ctor = cls.getConstructor(
            String::class.java,                     // menuName
            String::class.java,                     // type
            Int::class.javaPrimitiveType!!,         // icon（0 = 不设图标，避免宿主资源 id 错误）
            java.lang.Boolean::class.java,          // isDarkerTextColorAndEnter（可空）
            Boolean::class.javaPrimitiveType!!,     // isChecked
            Boolean::class.javaPrimitiveType!!,     // isDivider
            Boolean::class.javaPrimitiveType!!,     // isTitle
        )
        ctor.newInstance(ENTRY_TITLE, customType, 0, null, false, false, false)
    }.onFailure { XLog.e("add_menu: 构造 RichFormatMenuData 失败", it) }.getOrNull()

    /** 点击自定义项：关闭面板并打开批量排版界面 */
    private fun handleClick(param: XC_MethodHook.MethodHookParam) {
        val item = param.args.getOrNull(0) ?: return
        if (readType(item) != customType) return

        XLog.i("add_menu: 点击「批量与排版」")
        runCatching { dismissPopup(param.thisObject) }
            .onFailure { XLog.w("add_menu: 关闭面板失败: ${it.message}") }

        val context = runCatching {
            param.thisObject.javaClass.getDeclaredField("context")
                .apply { isAccessible = true }
                .get(param.thisObject) as? Context
        }.getOrNull()

        launch(context)
    }

    /** 三点菜单：在 onCreateOptionsMenu 执行完后追加「批量与排版」 */
    private fun addThreeDotEntry(param: XC_MethodHook.MethodHookParam) {
        val menu = param.args.getOrNull(0) as? Menu ?: return
        // 宿主每次重建菜单都先 clear()；注意宿主菜单系统会重写 add 传入的 itemId
        // （实测传入 0x7F0A0001 被改写为 0x7F090001），因此用标题去重与匹配
        for (i in 0 until menu.size()) {
            if (menu.getItem(i).title.toString() == ENTRY_TITLE) return
        }
        val context = runCatching {
            (param.thisObject as Any).javaClass.getMethod("getActivity")
                .invoke(param.thisObject) as? Context
        }.getOrNull()

        runCatching {
            val item = menu.add(Menu.NONE, customMenuId, 100, ENTRY_TITLE)
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            // 不设置 icon：宿主资源 id 易错（此前 0x7F0800C2 实为 COUI 动画
            // drawable，可能引起溢出菜单渲染异常），标题文字足够
            // 关键：直接挂点击监听，在菜单分发最前端短路，
            // 绕开宿主对未知 id 的处理（v1.3.3 退回主页的根因）
            item.setOnMenuItemClickListener { _ ->
                XLog.i("add_menu: 菜单项点击监听触发，打开批量排版")
                launch(context)
                true
            }
            // 诊断：打印菜单全貌，确认自定义项位置与溢出菜单行为
            val items = (0 until menu.size()).mapNotNull { i ->
                val mi = menu.getItem(i)
                "${mi.title}(${mi.itemId})"
            }
            XLog.i("add_menu: 三点菜单已添加「批量与排版」，菜单共 ${menu.size()} 项: $items")
        }.onFailure { XLog.e("add_menu: 三点菜单添加失败", it) }
    }

    /** 三点菜单：点击「批量与排版」时拦截并打开模块界面（Fragment 层） */
    private fun interceptMenuClick(param: XC_MethodHook.MethodHookParam) {
        val item = param.args.getOrNull(0) as? MenuItem ?: return
        if (isOurItem(item)) {
            param.result = true
            XLog.i("add_menu: Fragment 层拦截「批量与排版」点击")

            val fragment = param.thisObject
            val context = runCatching {
                fragment.javaClass.getMethod("getActivity").invoke(fragment) as? Context
            }.getOrNull()
            launch(context)
            return
        }
        // 诊断：记录编辑页 Fragment 收到的其他菜单点击
        logOtherClick("Fragment#onOptionsItemSelected", item, param.thisObject)
    }

    /** Activity 层兜底：FragmentActivity.onMenuItemSelected / Activity.onOptionsItemSelected */
    private fun interceptActivityClick(param: XC_MethodHook.MethodHookParam, source: String) {
        // FragmentActivity.onMenuItemSelected(int, MenuItem)；Activity.onOptionsItemSelected(MenuItem)
        val item = param.args.getOrNull(1) as? MenuItem
            ?: param.args.getOrNull(0) as? MenuItem ?: return
        if (isOurItem(item)) {
            param.result = true
            XLog.i("add_menu: $source 拦截「批量与排版」点击")
            launch(param.thisObject as? Context)
            return
        }
        logOtherClick(source, item, param.thisObject)
    }

    /**
     * 判断是否为我们的菜单项。宿主菜单系统会重写 add 传入的 itemId
     * （实测 0x7F0A0001 → 0x7F090001），因此以标题为准、id 为辅。
     */
    private fun isOurItem(item: MenuItem): Boolean =
        item.itemId == customMenuId || item.title.toString() == ENTRY_TITLE

    /** 诊断：编辑页相关 Activity/Fragment 收到的其他菜单点击（限流） */
    private fun logOtherClick(source: String, item: MenuItem, owner: Any?) {
        val cls = owner?.javaClass?.name ?: return
        if (!cls.contains("WVNoteViewEditFragment") &&
            !cls.contains("RichEditActivity") &&
            !cls.contains("NoteViewEditActivity")
        ) return
        val now = System.currentTimeMillis()
        if (now - clickLogAt < 1500) return
        clickLogAt = now
        XLog.i("add_menu: 菜单点击[$source] ${item.title}(id=${item.itemId}) owner=$cls")
    }

    private fun dismissPopup(manager: Any?) {
        val field = manager?.javaClass?.getDeclaredField("popupWindow") ?: return
        field.isAccessible = true
        val popup = field.get(manager) as? PopupWindow ?: return
        if (popup.isShowing) popup.dismiss()
    }

    private fun launch(context: Context?) {
        val ctx = context ?: return
        val now = System.currentTimeMillis()
        if (now - launchedAt < 800) return
        launchedAt = now

        runCatching {
            val intent = Intent().apply {
                setClassName(ModuleEntry.MODULE_PACKAGE, BATCH_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            XLog.i("add_menu: 已打开批量排版")
        }.onFailure {
            XLog.e("add_menu: 打开批量排版失败", it)
        }
    }

    private fun readType(item: Any?): String? = runCatching {
        val method: Method = item!!.javaClass.getMethod("getType")
        method.invoke(item) as? String
    }.getOrNull()

    companion object {
        private const val POPUP_CLASS =
            "com.nearme.note.activity.richedit.webview.RichTextFormatOptionMenu"
        private const val MANAGER_CLASS =
            "com.nearme.note.activity.richedit.webview.RichTextFormatOptionMenuManager"
        private const val MENU_DATA_CLASS =
            "com.nearme.note.activity.richedit.entity.RichFormatMenuData"
        private const val FRAGMENT_CLASS =
            "com.nearme.note.activity.richedit.webview.WVNoteViewEditFragment"
        private val MENU_FRAGMENT_CLASSES = listOf(
            FRAGMENT_CLASS,
            "com.nearme.note.activity.richedit.webview.WVQuickNoteViewEditFragment",
            "com.nearme.note.activity.richedit.coverpaint.WVNoteViewEditFragmentCoverPaint",
        )
        private const val BATCH_ACTIVITY = "com.fuoins.opponote.ui.BatchFormatActivity"
        private const val ENTRY_TITLE = "批量与排版"
    }
}
