package android.app;

import java.lang.reflect.Method;

/**
 * A helper class used to intercept well-known APIs.
 *
 * @hide
 */
public class ApiInterceptor {

    /**
     * Initialize the API interceptor.
     *
     * @param packageName The package name of the current application.
     *
     * @return true if the interceptor initializes successfully, and false otherwise.
     */
    /** package */ static boolean initialize(String packageName) {
        return nativeInitialize(packageName);
    }

    /**
     * Hook a given target method.
     *
     * @param target The method to hook.
     * @param hook The hook that will be called instead of the target. This must
     * be a static method.
     * @param backup A backup of the method that was hooked. This must be a
     * static method.
     *
     * @return true if the method was hooked successfully, and false otherwise.
     */
    /** package */ static boolean hookMethod(Method target, Method hook, Method backup) {
        return nativeHookMethod(target, hook, backup);
    }

    private static native boolean nativeInitialize(String packageName);

    private static native boolean nativeHookMethod(Method targetMethod, Method hookMethod, Method backupMethod);
}
