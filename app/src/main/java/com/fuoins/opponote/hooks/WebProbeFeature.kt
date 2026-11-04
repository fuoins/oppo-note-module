package com.fuoins.opponote.hooks

import android.webkit.WebView
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.WebViewWatcher
import com.fuoins.opponote.util.XLog
import java.util.Collections
import java.util.WeakHashMap

/**
 * 编辑页结构探测（内部功能，不暴露开关）。
 *
 * 在页面加载完成后注入只读脚本，把元素统计与待办候选节点的结构写进运行日志，
 * 用于后续功能（待办、排版、首页）的样式适配。只读 DOM，不改内容。
 */
class WebProbeFeature : Feature {

    override val id = "web_probe"
    override val title = "编辑页结构探测"
    override val summary = "内部调试：把编辑页 DOM 结构写入运行日志"
    override val enabledByDefault = true

    /** 每个 WebView 只探测一次 */
    private val probed = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<WebView, Boolean>())
    )

    override fun install(ctx: HookContext) {
        WebViewWatcher.install(ctx) { view -> probe(view) }
        XLog.i("web_probe: 就绪")
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
