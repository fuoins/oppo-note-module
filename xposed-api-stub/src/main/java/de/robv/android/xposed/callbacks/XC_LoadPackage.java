package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

/**
 * 编译期桩：LoadPackageParam 的公开字段与真实实现保持一致。
 */
public abstract class XC_LoadPackage {

    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
        public boolean isFirstApplication;
    }
}
