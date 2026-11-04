package com.fuoins.opponote.core

import android.app.Application
import com.fuoins.opponote.util.XLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 目标进程内的 Hook 上下文。全部使用原生反射查找成员，
 * 只依赖 XposedBridge.hookMethod 完成注册，最大限度降低对框架 API 的耦合。
 */
class HookContext(
    val app: Application,
    val classLoader: ClassLoader,
    val prefs: Prefs,
    val processName: String,
    val targetPackage: String,
) {

    val isMainProcess: Boolean
        get() = processName == targetPackage

    fun findClass(className: String): Class<*>? =
        runCatching { Class.forName(className, false, classLoader) }
            .onFailure { XLog.w("class not found: $className") }
            .getOrNull()

    fun findClassOrThrow(className: String): Class<*>? = findClass(className)

    fun findMethod(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method? = findClass(className)?.let { findMethod(it, methodName, *parameterTypes) }

    fun findMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>,
    ): Method? = runCatching { clazz.getDeclaredMethod(methodName, *parameterTypes) }
        .onFailure {
            XLog.w("method not found: ${clazz.name}#$methodName(${parameterTypes.joinToString { it.simpleName }})")
        }
        .getOrNull()
        ?.apply { isAccessible = true }

    fun findMethods(className: String, methodName: String): List<Method> {
        val clazz = findClass(className) ?: return emptyList()
        return clazz.declaredMethods
            .filter { it.name == methodName }
            .onEach { it.isAccessible = true }
    }

    fun findMethods(clazz: Class<*>, methodName: String): List<Method> =
        clazz.declaredMethods.filter { it.name == methodName }.onEach { it.isAccessible = true }

    fun findConstructor(className: String, vararg parameterTypes: Class<*>): Constructor<*>? =
        findClass(className)?.let { clazz ->
            runCatching { clazz.getDeclaredConstructor(*parameterTypes) }
                .onFailure { XLog.w("constructor not found: $className") }
                .getOrNull()
                ?.apply { isAccessible = true }
        }

    fun hook(
        method: Method,
        before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
        after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
    ): Boolean = runCatching {
        method.isAccessible = true
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                before?.invoke(param)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                after?.invoke(param)
            }
        })
        true
    }.getOrElse {
        XLog.e("hook failed: ${method.declaringClass.name}#${method.name}", it)
        false
    }

    fun hookConstructor(
        constructor: Constructor<*>,
        after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
    ): Boolean = runCatching {
        constructor.isAccessible = true
        XposedBridge.hookMethod(constructor, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                after?.invoke(param)
            }
        })
        true
    }.getOrElse {
        XLog.e("hook ctor failed: ${constructor.declaringClass.name}", it)
        false
    }

    /** 按类名 + 方法名 Hook，方法不存在时静默跳过（返回 false） */
    fun hookMethod(
        className: String,
        methodName: String,
        vararg parameterTypes: Class<*>,
        before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
        after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
    ): Boolean {
        val method = findMethod(className, methodName, *parameterTypes) ?: return false
        return hook(method, before, after)
    }

    /** Hook 某类中所有同名方法 */
    fun hookAllMethods(
        className: String,
        methodName: String,
        before: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
        after: ((XC_MethodHook.MethodHookParam) -> Unit)? = null,
    ): Int {
        val methods = findMethods(className, methodName)
        if (methods.isEmpty()) {
            XLog.w("no method $className#$methodName")
            return 0
        }
        return methods.count { hook(it, before, after) }
    }
}
