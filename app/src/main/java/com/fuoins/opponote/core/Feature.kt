package com.fuoins.opponote.core

/**
 * 一个独立功能单元。新增功能只需实现该接口并注册到 [FeatureRegistry]，
 * 无需改动模块入口。
 */
interface Feature {

    /** 唯一标识，同时作为偏好设置的键名后缀，请勿随意修改 */
    val id: String

    /** 展示给用户的标题 */
    val title: String

    /** 一句话说明功能作用 */
    val summary: String

    /** 默认是否启用 */
    val enabledByDefault: Boolean get() = false

    /** 在目标进程内安装 Hook */
    fun install(ctx: HookContext)
}
