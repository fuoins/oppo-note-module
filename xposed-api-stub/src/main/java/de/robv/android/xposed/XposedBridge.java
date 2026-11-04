package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.Set;

/**
 * 编译期桩：方法签名与 XposedBridge API 82 保持一致，运行时由框架实现。
 * 注意：本类绝不可打包进模块 APK，否则框架注入的 hook 会失效。
 */
public final class XposedBridge {

    public static final int XPOSED_BRIDGE_VERSION = 82;

    private XposedBridge() {
    }

    public static void log(String text) {
    }

    public static void log(Throwable t) {
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }

    public static void unhookMethod(Member hookMethod, XC_MethodHook callback) {
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(
            Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return null;
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(
            Class<?> hookClass, XC_MethodHook callback) {
        return null;
    }

    public static Object invokeOriginalMethod(Member method, Object thisObject, Object[] args)
            throws Throwable {
        return null;
    }
}
