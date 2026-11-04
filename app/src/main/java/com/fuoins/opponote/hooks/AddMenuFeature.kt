package com.fuoins.opponote.hooks

import android.content.Context
import android.content.Intent
import android.widget.AdapterView
import android.widget.PopupWindow
import com.fuoins.opponote.ModuleEntry
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog
import de.robv.android.xposed.XC_MethodHook
import java.lang.reflect.Method

/**
 * 在笔记编辑页的「添加」面板里加入「批量与排版」。
 *
 * 面板不是静态菜单：每次显示都会由 ExtraOptionMenu.generatePopupIfNeed()
 * 重新构建一个 ArrayList<PopupListItem>，最后交给
 * COUIPopupListWindow.setItemList(List)。
 * 因此在这一步插入自定义项，既能复用原生样式，也不会被重建冲掉。
 */
class AddMenuFeature : Feature {

    override val id = "add_menu"
    override val title = "「添加」面板入口"
    override val summary = "在笔记编辑页的添加面板中加入「批量与排版」"
    override val enabledByDefault = true

    /** 自定义项 id，避开原生 0..6，且带项目前缀便于识别 */
    private val customId = 0x4F50_0001

    /** 只在 generatePopupIfNeed 的执行栈内插入，避免影响便签其它 popup */
    private val building = ThreadLocal<Boolean>()

    private var launchedAt = 0L

    override fun install(ctx: HookContext) {
        val menuClass = ctx.findClass(MENU_CLASS)
        if (menuClass == null) {
            XLog.w("add_menu: 未找到 $MENU_CLASS，跳过")
            return
        }

        val marked = ctx.hookAllMethods(
            MENU_CLASS,
            "generatePopupIfNeed",
            before = { building.set(true) },
            after = { building.set(false) },
        )
        XLog.i("add_menu: generatePopupIfNeed hooked=$marked")

        val injected = hookListWindow(ctx)
        XLog.i("add_menu: setItemList hooked=$injected")

        val clicked = ctx.hookAllMethods(
            MENU_CLASS,
            "onItemClick",
            before = { param: XC_MethodHook.MethodHookParam -> handleClick(param) },
        )
        XLog.i("add_menu: onItemClick hooked=$clicked")
    }

    /** 在 setItemList 里追加自定义项 */
    private fun hookListWindow(ctx: HookContext): Int {
        val windowClass = ctx.findClass(LIST_WINDOW_CLASS) ?: return 0
        val methods = ctx.findMethods(windowClass, "setItemList")
            .filter { it.parameterTypes.size == 1 && it.parameterTypes[0] == List::class.java }
        if (methods.isEmpty()) {
            XLog.w("add_menu: $LIST_WINDOW_CLASS#setItemList(List) 未找到")
            return 0
        }

        var count = 0
        methods.forEach { method ->
            if (ctx.hook(method, before = { param: XC_MethodHook.MethodHookParam -> inject(param, ctx) })) count++
        }
        return count
    }

    @Suppress("UNCHECKED_CAST")
    private fun inject(param: XC_MethodHook.MethodHookParam, ctx: HookContext) {
        if (building.get() != true) return
        val list = param.args[0] as? MutableList<Any?> ?: return

        // 重建后可能已经加过，避免重复
        if (list.any { readId(it) == customId }) return

        val iconRes = runCatching { android.R.drawable.ic_menu_edit }.getOrDefault(0)
        val item = buildItem(ctx.classLoader, iconRes) ?: run {
            XLog.w("add_menu: 构造 PopupListItem 失败")
            return
        }
        runCatching { list.add(item) }
            .onSuccess { XLog.i("add_menu: 已注入自定义项，共 ${list.size} 项") }
            .onFailure { XLog.e("add_menu: 注入失败", it) }
    }

    /** 反射构造 com.coui.appcompat.poplist.PopupListItem */
    private fun buildItem(classLoader: ClassLoader, iconRes: Int): Any? = runCatching {
        val builderClass = Class.forName(BUILDER_CLASS, false, classLoader)
        val builder = builderClass.getDeclaredConstructor().newInstance()

        fun call(name: String, type: Class<*>, value: Any) {
            builderClass.getMethod(name, type).invoke(builder, value)
        }
        call("setId", Int::class.javaPrimitiveType!!, customId)
        call("setGroupId", Int::class.javaPrimitiveType!!, 3)
        call("setTitle", String::class.java, ENTRY_TITLE)
        if (iconRes != 0) call("setIconId", Int::class.javaPrimitiveType!!, iconRes)

        builderClass.getMethod("build").invoke(builder)
    }.onFailure { XLog.e("add_menu: buildItem 异常", it) }.getOrNull()

    /** 点击自定义项时打开批量排版界面，并吞掉原生逻辑 */
    private fun handleClick(param: XC_MethodHook.MethodHookParam) {
        val parent = param.args.getOrNull(0) as? AdapterView<*> ?: return
        val position = param.args.getOrNull(2) as? Int ?: return
        val item = runCatching { parent.adapter?.getItem(position) }.getOrNull() ?: return
        if (readId(item) != customId) return

        // 阻止原生分发，自行处理
        param.result = null

        val now = System.currentTimeMillis()
        if (now - launchedAt < 800) return
        launchedAt = now

        runCatching { dismissPopup(param.thisObject) }
        launch(parent.context)
    }

    private fun dismissPopup(menu: Any?) {
        val field = menu?.javaClass?.getDeclaredField("popup") ?: return
        field.isAccessible = true
        val popup = field.get(menu) as? PopupWindow ?: return
        if (popup.isShowing) popup.dismiss()
    }

    private fun launch(context: Context?) {
        val ctx = context ?: return
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

    private fun readId(item: Any?): Int? = runCatching {
        val method: Method = item!!.javaClass.getMethod("getId")
        method.invoke(item) as? Int
    }.getOrNull()

    companion object {
        private const val MENU_CLASS = "com.nearme.note.activity.richedit.webview.ExtraOptionMenu"
        private const val LIST_WINDOW_CLASS = "com.coui.appcompat.poplist.COUIPopupListWindow"
        private const val BUILDER_CLASS = "com.coui.appcompat.poplist.PopupListItem\$Builder"
        private const val BATCH_ACTIVITY = "com.fuoins.opponote.ui.BatchFormatActivity"
        private const val ENTRY_TITLE = "批量与排版"
    }
}
