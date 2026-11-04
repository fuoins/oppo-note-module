package com.fuoins.opponote.hooks

import android.webkit.WebView
import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.WebViewWatcher
import com.fuoins.opponote.util.XLog
import org.json.JSONObject

/**
 * 待办增强：勾选圈移到文字右侧、一行显示多个待办。
 *
 * 编辑页正文是 tiptap/ProseMirror 富文本（URL 为 assets/tiptap/index.html），
 * 待办块结构（来自真机日志）：
 *
 *   div.block-wrapper (topLevel)
 *     div.block-wrapper-checkbox
 *       div.checkbox-inner.unchecked
 *     文字
 *
 * 原生 CSS 把 checkbox 绝对定位到块的 inline-end，但父块缺 position:relative
 * 时定位失效，圆圈就落回 DOM 默认位置（文字左侧）。
 * 这里注入一段补丁 CSS，只改显示、不动数据，因此不影响云同步。
 */
class TodoEnhanceFeature : Feature {

    override val id = "todo_enhance"
    override val title = "待办增强"
    override val summary = "勾选圈移到文字右侧，一行显示多个待办"
    override val enabledByDefault = true

    override fun install(ctx: HookContext) {
        WebViewWatcher.install(ctx) { view -> injectCss(view) }
        XLog.i("todo_enhance: 等待编辑页加载后注入 CSS")
    }

    private fun injectCss(view: WebView) {
        val js = """
        (function(){
          try {
            var id = 'fuoins-todo-css';
            var old = document.getElementById(id);
            if (old && old.parentNode) old.parentNode.removeChild(old);
            var s = document.createElement('style');
            s.id = id;
            s.textContent = ${JSONObject.quote(PATCH_CSS)};
            (document.head || document.documentElement).appendChild(s);
            return 'ok';
          } catch (e) { return 'ERR ' + e; }
        })()
        """.trimIndent()

        runCatching {
            view.evaluateJavascript(js) { result ->
                XLog.i("todo_css: $result")
            }
        }.onFailure { XLog.w("todo_css: 注入失败", it) }
    }

    companion object {
        /**
         * 1. 含勾选圈的待办块建立定位上下文，并横向排列（一行多个）；
         * 2. 勾选圈固定到文字右端；
         * 3. 待办块之间留出间距。
         */
        private val PATCH_CSS = listOf(
            ".tiptapContent .block-wrapper:has(>.block-wrapper-checkbox){" +
                "position:relative!important;" +
                "display:inline-block!important;" +
                "width:auto!important;" +
                "margin-inline-end:1.6em!important;" +
                "vertical-align:top!important}",
            ".tiptapContent .block-wrapper .block-wrapper-checkbox{" +
                "left:auto!important;" +
                "right:0!important;" +
                "inset-inline-end:0!important;" +
                "inset-inline-start:auto!important;" +
                "height:100%!important;" +
                "justify-content:flex-end!important}",
        ).joinToString("\n")
    }
}
