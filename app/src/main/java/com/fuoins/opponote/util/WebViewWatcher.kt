package com.fuoins.opponote.util

import android.webkit.WebView
import android.webkit.WebViewClient
import com.fuoins.opponote.core.HookContext
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.Collections

/**
 * 便签编辑页 WebView 的统一监听。
 *
 * 便签用自定义 WebViewClient（混淆类名），直接 Hook WebViewClient.onPageFinished
 * 无法覆盖子类重写，因此从 WebView.setWebViewClient 入手，
 * 拿到真实 client 实例后按实际类 Hook，页面加载完成后回调。
 */
object WebViewWatcher {

    /** 已 Hook 过的 client 实现类，避免重复注册 */
    private val hookedClients = Collections.synchronizedSet(HashSet<String>())

    /** 每次页面加载完成时触发；回调可能被多次调用（不同页面） */
    fun install(ctx: HookContext, onPageFinished: (WebView) -> Unit) {
        val setClient = ctx.findMethod(
            WebView::class.java,
            "setWebViewClient",
            WebViewClient::class.java,
        )
        if (setClient == null) {
            XLog.w("WebViewWatcher: setWebViewClient 未找到")
            return
        }

        ctx.hook(setClient, after = { param: XC_MethodHook.MethodHookParam ->
            val client = param.args.getOrNull(0) as? WebViewClient ?: return@hook
            hookClient(client, onPageFinished)
        })
    }

    private fun hookClient(client: WebViewClient, onPageFinished: (WebView) -> Unit) {
        val clazz = client.javaClass
        if (!hookedClients.add(clazz.name)) return

        val methods = clazz.declaredMethods.filter {
            it.name == "onPageFinished" && it.parameterTypes.size == 2
        }
        if (methods.isEmpty()) {
            XLog.i("WebViewWatcher: ${clazz.simpleName} 未重写 onPageFinished")
            return
        }

        methods.forEach { method ->
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.args.getOrNull(0) as? WebView ?: return
                        runCatching { onPageFinished(view) }
                            .onFailure { XLog.w("WebViewWatcher: callback failed", it) }
                    }
                })
                XLog.i("WebViewWatcher: hooked ${clazz.simpleName}#onPageFinished")
            }.onFailure { XLog.w("WebViewWatcher: hook ${clazz.simpleName} 失败", it) }
        }
    }
}
