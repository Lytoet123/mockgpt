package com.lilstiffy.mockgps.xposed

import android.location.Location
import android.os.Build
import android.os.Bundle
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed module that hooks into the target app's process to hide mock location flags.
 *
 * This runs INSIDE the survey app's process, so it intercepts Location objects
 * AFTER the Android framework has marked them as mock. This is the only way
 * to reliably bypass isFromMockProvider() / isMock() detection.
 *
 * Bypasses:
 * - Method 4: Play Integrity / SafetyNet mock detection
 * - Method 5: App-level isFromMockProvider check
 * - Settings.Secure "mock_location" check
 * - LocationManager.isProviderEnabled() mock provider check
 */
class LocationMockHider : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Skip system framework and our own app
        if (lpparam.packageName == "android" ||
            lpparam.packageName == "com.lilstiffy.mockgps" ||
            lpparam.packageName == "com.lilstiffy.mockgps.xposed") {
            return
        }

        XposedBridge.log("[MockHider] Hooking package: ${lpparam.packageName}")

        // === HOOK 1: Location.isFromMockProvider() (API < 31) ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "isFromMockProvider",
                XC_MethodReplacement.returnConstant(false)
            )
            XposedBridge.log("[MockHider] Hooked isFromMockProvider()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook isFromMockProvider: ${e.message}")
        }

        // === HOOK 2: Location.isMock() (API 31+) ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "isMock",
                XC_MethodReplacement.returnConstant(false)
            )
            XposedBridge.log("[MockHider] Hooked isMock()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] isMock not found (pre-API 31): ${e.message}")
        }

        // === HOOK 3: Location.setMock() - prevent framework from setting mock flag ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "setMock",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        // Always set to false, regardless of what the framework wants
                        param.args[0] = false
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked setMock()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] setMock not found: ${e.message}")
        }

        // === HOOK 4: Location.getExtras() - remove mock-related extras ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location",
                lpparam.classLoader,
                "getExtras",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val extras = param.result as? Bundle ?: return
                        // Remove any mock-related keys
                        extras.remove("mockProvider")
                        extras.remove("isMock")
                        extras.remove("mockLocation")
                        param.result = extras
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked getExtras()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook getExtras: ${e.message}")
        }

        // === HOOK 5: Settings.Secure.getString() - hide mock_location setting ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings.Secure",
                lpparam.classLoader,
                "getString",
                android.content.ContentResolver::class.java,
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "mock_location") {
                            // Report mock location as disabled
                            param.result = "0"
                        }
                        if (key == "development_settings_enabled") {
                            // Optionally hide developer options enabled state
                            // Some apps check this as an indirect mock indicator
                            param.result = "0"
                        }
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked Settings.Secure.getString()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook Settings.Secure: ${e.message}")
        }

        // === HOOK 6: Settings.Secure.getInt() - for apps that use getInt variant ===
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings.Secure",
                lpparam.classLoader,
                "getInt",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "mock_location") {
                            param.result = 0
                        }
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked Settings.Secure.getInt()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook Settings.Secure.getInt: ${e.message}")
        }

        // === HOOK 7: PackageManager - hide mock GPS apps from app list scan (Method 5) ===
        hookPackageManager(lpparam)

        // === HOOK 8: Location constructor copy - clean mock flag on copy ===
        try {
            XposedHelpers.findAndHookConstructor(
                "android.location.Location",
                lpparam.classLoader,
                Location::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // After copying a location, ensure mock flag is cleared
                        try {
                            val location = param.thisObject as Location
                            val field = Location::class.java.getDeclaredField("mIsMock")
                            field.isAccessible = true
                            field.setBoolean(location, false)
                        } catch (_: NoSuchFieldException) {
                            try {
                                val field = Location::class.java.getDeclaredField("mIsFromMockProvider")
                                field.isAccessible = true
                                field.setBoolean(location, false)
                            } catch (_: Exception) { }
                        } catch (_: Exception) { }
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked Location copy constructor")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook Location constructor: ${e.message}")
        }

        XposedBridge.log("[MockHider] All hooks installed for ${lpparam.packageName}")
    }

    /**
     * Hook PackageManager to hide known mock location apps from the survey app's
     * app scanning (Method 5 bypass - app list detection).
     */
    private fun hookPackageManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        val mockAppPackages = setOf(
            "com.lilstiffy.mockgps",
            "com.lexa.fakegps",
            "com.incorporateapps.fakegps.fre",
            "com.fakegps.mock",
            "com.blogspot.newapphorizons.fakegps",
            "com.gsmartstudio.fakegps",
            "com.lkr.fakelocation",
            "com.evezzon.fakegps",
            "ru.gavrikov.mocklocations",
            "com.theappninjas.fakegpsjoystick",
            "com.theappninjas.gpsjoystick"
        )

        // Hook getInstalledApplications
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader,
                "getInstalledApplications",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val apps = param.result as? MutableList<*> ?: return
                        apps.removeAll { app ->
                            try {
                                val packageName = XposedHelpers.getObjectField(app, "packageName") as? String
                                packageName in mockAppPackages
                            } catch (_: Exception) { false }
                        }
                        param.result = apps
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked getInstalledApplications()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook getInstalledApplications: ${e.message}")
        }

        // Hook getInstalledPackages
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader,
                "getInstalledPackages",
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val packages = param.result as? MutableList<*> ?: return
                        packages.removeAll { pkg ->
                            try {
                                val packageName = XposedHelpers.getObjectField(pkg, "packageName") as? String
                                packageName in mockAppPackages
                            } catch (_: Exception) { false }
                        }
                        param.result = packages
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked getInstalledPackages()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook getInstalledPackages: ${e.message}")
        }

        // Hook getApplicationInfo - throw NameNotFoundException for mock apps
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                lpparam.classLoader,
                "getApplicationInfo",
                String::class.java,
                Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val packageName = param.args[0] as? String
                        if (packageName in mockAppPackages) {
                            param.throwable = android.content.pm.PackageManager.NameNotFoundException(packageName)
                        }
                    }
                }
            )
            XposedBridge.log("[MockHider] Hooked getApplicationInfo()")
        } catch (e: Exception) {
            XposedBridge.log("[MockHider] Failed to hook getApplicationInfo: ${e.message}")
        }
    }
}
