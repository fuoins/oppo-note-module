package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * 编译期桩：与 XposedBridge API 82 的 XC_MethodHook 签名保持一致。
 */
public abstract class XC_MethodHook {

    public XC_MethodHook() {
    }

    public XC_MethodHook(int priority) {
    }

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    public static final class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;

        private Object result;
        private Throwable throwable;

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public void setThrowable(Throwable throwable) {
            this.throwable = throwable;
        }

        public boolean hasThrowable() {
            return throwable != null;
        }

        public Object getObjectExtra(String key) {
            return null;
        }

        public void setObjectExtra(String key, Object o) {
        }
    }

    public static final class Unhook {
        private final Member method;
        private final XC_MethodHook callback;

        public Unhook(Member method, XC_MethodHook callback) {
            this.method = method;
            this.callback = callback;
        }

        public Member getHookedMethod() {
            return method;
        }

        public XC_MethodHook getCallback() {
            return callback;
        }

        public void unhook() {
        }
    }
}
