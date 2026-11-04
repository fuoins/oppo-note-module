package com.fuoins.opponote.core

/**
 * 一个独立功能单元。新增功能只需实现该接口并注册到 [FeatureRegistry]。
 */
interface Feature {

    /** 唯一标识，同时作为偏好设置的键名后缀 */
    val id: String

    val title: String

    val summary: String

    val enabledByDefault: Boolean get() = true

    /** 在目标进程内安装 Hook */
    fun install(ctx: HookContext)
}
