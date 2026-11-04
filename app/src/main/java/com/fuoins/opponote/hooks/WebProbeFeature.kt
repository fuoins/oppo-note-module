package com.fuoins.opponote.hooks

import android.webkit.WebView
import android.webkit.WebViewClient
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.Collections
import java.util.WeakHashMap

/**
 * 编辑页结构探测。
 *
 * 便签的正文是 WebView 富文本，待办块的 DOM 结构随版本变化、且类名混淆，
 * 静态分析拿不到可靠的插入点。这里在页面加载完成后注入一段只读脚本，
 * 把元素结构写进「运行日志」，据此确定 CSS 注入规则。
 *
 * 只读 DOM，不修改任何内容，每个 WebView 实例只探测一次。
 */
class WebProbeFeature : Feature {

    override val id = "web_probe"
    override val title = "编辑页结构探测"
    override val summary = "把编辑页 DOM 结构写入运行日志，用于适配待办样式"
    override val enabledByDefault = true

    /** 已 hook 过的 WebViewClient 实现类，避免重复注册 */
    private val hookedClients = Collections.synchronizedSet(HashSet<String>())

    /** 每个 WebView 只探测一次 */
    private val probed = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    )

    override fun install(ctx: HookContext) {
        val setClient = ctx.findMethod(
            WebView::class.java,
            "setWebViewClient",
            WebViewClient::class.java,
        )
        if (setClient == null) {
            XLog.w("web_probe: WebView.setWebViewClient 未找到")
            return
        }

        val ok = ctx.hook(setClient, after = { param: XC_MethodHook.MethodHookParam ->
            val client = param.args.getOrNull(0) as? WebViewClient ?: return@hook
            hookClient(client)
        })
        XLog.i("web_probe: setWebViewClient hooked=$ok")
    }

    private fun hookClient(client: WebViewClient) {
        val clazz = client.javaClass
        val name = clazz.name
        if (!hookedClients.add(name)) return

        val methods = clazz.declaredMethods.filter {
            it.name == "onPageFinished" && it.parameterTypes.size == 2
        }
        if (methods.isEmpty()) {
            XLog.i("web_probe: ${clazz.simpleName} 未重写 onPageFinished")
            return
        }

        methods.forEach { method ->
            runCatching {
                method.isAccessible = true
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val view = param.args.getOrNull(0) as? WebView ?: return
                        probe(view)
                    }
                })
                XLog.i("web_probe: hooked ${clazz.simpleName}#onPageFinished")
            }.onFailure { XLog.w("web_probe: hook ${clazz.simpleName} 失败", it) }
        }
    }

    private fun probe(view: WebView) {
        if (!probed.add(view)) return
        runCatching {
            view.evaluateJavascript(PROBE_JS) { result ->
                XLog.i("web_probe: ${result?.take(3200)}")
            }
        }.onFailure { XLog.w("web_probe: 注入脚本失败", it) }
    }

    companion object {
        /**
         * 只读探测：统计标签/类名分布，并抓取待办候选元素的结构。
         * 用 JSON 返回，避免换行在日志里丢失层次。
         */
        private val PROBE_JS = """
        (function(){
          try {
            var els = document.querySelectorAll('*');
            var counts = {};
            for (var i = 0; i < els.length; i++) {
              var e = els[i];
              var cls = (typeof e.className === 'string') ? e.className.trim() : '';
              var key = e.tagName.toLowerCase() + (cls ? '.' + cls.split(/\s+/).join('.') : '');
              counts[key] = (counts[key] || 0) + 1;
            }
            var pairs = [];
            for (var k in counts) pairs.push([k, counts[k]]);
            pairs.sort(function(a, b){ return b[1] - a[1]; });
            pairs = pairs.slice(0, 24);
            var stat = pairs.map(function(p){ return p[0] + 'x' + p[1]; }).join(' , ');

            var cand = document.querySelector('[class*="todo"],[class*="Todo"],[class*="check"],[class*="Check"]');
            var candHtml = cand ? cand.outerHTML.replace(/\s+/g, ' ').slice(0, 900) : 'none';

            var body = document.body ? document.body.innerHTML.replace(/\s+/g, ' ').slice(0, 700) : '';

            return JSON.stringify({
              url: location.href.slice(0, 160),
              count: els.length,
              stat: stat,
              todo: candHtml,
              head: body
            });
          } catch (err) {
            return 'PROBE_ERR ' + err;
          }
        })()
        """.trimIndent()
    }
}
