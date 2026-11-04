package com.fuoins.opponote.hooks

import android.content.Context
import android.content.Intent
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
 * 面板类型判别：插入面板列表包含 album / take_photo / paint_entry / title_table /
 * title_file / scan_document / ocr 等 type，而「/」斜杠格式菜单只有 title /
 * subtitle / bullet / numbered 等，据此只对插入面板注入。
 */
class AddMenuFeature : Feature {

    override val id = "add_menu"
    override val title = "「添加」面板入口"
    override val summary = "在笔记编辑页的添加面板中加入「批量与排版」"
    override val enabledByDefault = true

    /** 自定义项的 type 标识，避开原生类型 */
    private val customType = "batch_format"

    /** 自定义项在「添加」面板中显示的图标（宿主资源：提取文字图标） */
    private val iconRes = 0x7F0800C2 // 2131233410

    private var launchedAt = 0L

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
    }

    /** 在弹窗 setData 时追加自定义项 */
    private fun injectItem(param: XC_MethodHook.MethodHookParam, ctx: HookContext) {
        val list = param.args.getOrNull(0) as? MutableList<Any?> ?: return

        // 重建后可能已经加过，避免重复
        if (list.any { readType(it) == customType }) return

        // 只对「添加」插入面板注入，斜杠格式菜单不动
        if (!isInsertPanelList(list)) return

        val item = buildItem(ctx.classLoader) ?: return
        runCatching { list.add(item) }
            .onSuccess { XLog.i("add_menu: 已注入「批量与排版」，面板共 ${list.size} 项") }
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
            Int::class.javaPrimitiveType!!,         // icon
            java.lang.Boolean::class.java,          // isDarkerTextColorAndEnter（可空）
            Boolean::class.javaPrimitiveType!!,     // isChecked
            Boolean::class.javaPrimitiveType!!,     // isDivider
            Boolean::class.javaPrimitiveType!!,     // isTitle
        )
        ctor.newInstance(ENTRY_TITLE, customType, iconRes, null, false, false, false)
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
        private const val BATCH_ACTIVITY = "com.fuoins.opponote.ui.BatchFormatActivity"
        private const val ENTRY_TITLE = "批量与排版"
    }
}
