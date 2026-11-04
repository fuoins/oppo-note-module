package com.fuoins.opponote.core

import com.fuoins.opponote.hooks.AddMenuFeature
import com.fuoins.opponote.hooks.DiagnosticsFeature
import com.fuoins.opponote.hooks.HomeStyleFeature
import com.fuoins.opponote.hooks.SettingsEntryFeature
import com.fuoins.opponote.hooks.TodoEnhanceFeature
import com.fuoins.opponote.hooks.WebProbeFeature
import com.fuoins.opponote.util.XLog

/**
 * 功能注册表。新增功能在这里登记即可。
 */
object FeatureRegistry {

    val all: List<Feature> = listOf(
        SettingsEntryFeature,
        AddMenuFeature(),
        TodoEnhanceFeature(),
        HomeStyleFeature(),
        WebProbeFeature(),
        DiagnosticsFeature,
    )

    fun find(id: String): Feature? = all.firstOrNull { it.id == id }

    fun installAll(ctx: HookContext) {
        XLog.i("installing ${all.size} feature(s) in ${ctx.processName}")
        all.forEach { feature ->
            val enabled = ctx.prefs.getBoolean(key(feature.id), feature.enabledByDefault)
            if (!enabled) {
                XLog.i("skip [${feature.id}]: disabled")
                return@forEach
            }
            val result = runCatching { feature.install(ctx) }
            if (result.isSuccess) {
                XLog.i("installed [${feature.id}]")
            } else {
                XLog.e("feature [${feature.id}] failed", result.exceptionOrNull())
            }
        }
    }

    fun key(featureId: String): String = "feature_$featureId"
}
