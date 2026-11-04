package com.fuoins.opponote.hooks

import com.fuoins.opponote.core.Feature
import com.fuoins.opponote.core.HookContext
import com.fuoins.opponote.util.XLog

/**
 * 首页样式增强。
 *
 * 首页列表由 RecyclerView + 混淆的 ViewBinding 渲染，适配需要真机日志。
 * 当前先启用开关并记录运行状态，样式规则待 DOM/View 树信息明确后注入。
 */
class HomeStyleFeature : Feature {

    override val id = "home_style"
    override val title = "首页样式"
    override val summary = "让笔记列表更醒目（样式规则适配中）"
    override val enabledByDefault = true

    override fun install(ctx: HookContext) {
        XLog.i("home_style: 已启用，首页列表样式规则适配中")
        // TODO: 依据真机 View 树定位首页列表条目后注入样式
    }
}
