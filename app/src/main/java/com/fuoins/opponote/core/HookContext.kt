package com.fuoins.opponote.core

import com.fuoins.opponote.util.XLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * 单个目标进程内的 Hook 上下文，封装类查找、方法查找与 Hook 注册，
 * 让具体功能只需关心目标类名与方法名。
 */
class HookContext(
    val module: XposedModule,
    val classLoader: ClassLoader,
    val prefs: Prefs,
    val processName: String,
    val targetPackage: String,
) {

    /** 是否运行在便签主进程（子进程通常不加载 UI 相关 Hook） */
    val isMainProcess: Boolean
        get() = processName == targetPackage

    fun findClass(className: String): Class<*>? =
        runCatching { Class.forName(className, false, classLoader) }
            .onFailure { XLog.w("class not found: $className") }
            .getOrNull()

    fun findMethod(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method? {
        val clazz = findClass(className) ?: return null
        return runCatching { clazz.getDeclaredMethod(methodName, *parameterTypes) }
            .onFailure { XLog.w("method not found: $className#$methodName(${parameterTypes.joinToString { it.simpleName }})") }
            .getOrNull()
            ?.apply { isAccessible = true }
    }

    fun findMethods(className: String, methodName: String): List<Method> {
        val clazz = findClass(className) ?: return emptyList()
        return clazz.declaredMethods
            .filter { it.name == methodName }
            .onEach { it.isAccessible = true }
    }

    fun findConstructor(className: String, vararg parameterTypes: Class<*>): Constructor<*>? {
        val clazz = findClass(className) ?: return null
        return runCatching { clazz.getDeclaredConstructor(*parameterTypes) }
            .onFailure { XLog.w("constructor not found: $className") }
            .getOrNull()
            ?.apply { isAccessible = true }
    }

    /**
     * 注册 Hook。hooker 内可读取/修改参数与返回值，必须调用 chain.proceed()
     * 才会执行原方法（除非有意拦截）。
     */
    fun hook(
        executable: Executable,
        hooker: (XposedInterface.Chain) -> Any?,
    ): XposedInterface.HookHandle? =
        runCatching {
            executable.isAccessible = true
            module.hook(executable).intercept { chain -> hooker(chain) }
        }.onFailure {
            XLog.e("hook failed: ${executable.toGenericString()}", it)
        }.getOrNull()

    /** 便捷写法：按类名 + 方法名 Hook，方法不存在时静默跳过 */
    fun hookMethod(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
        hooker: (XposedInterface.Chain) -> Any?,
    ): XposedInterface.HookHandle? =
        findMethod(className, methodName, *parameterTypes)?.let { hook(it, hooker) }
}
