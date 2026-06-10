@file:JvmName("HookMain")

package com.sam.lifelogger.hook

import android.content.Context
import android.content.Intent
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookMain : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "SqueezeHook"
        private const val ACTION_SQUEEZE_DETECTED = "com.sam.lifelogger.SQUEEZE_DETECTED"
        private const val LifeloggerPackage = "com.sam.lifelogger"
        private const val EXTRA_EVENT_TYPE = "event_type"
        private const val EXTRA_PROGRESS = "progress"
        private const val EVENT_DETECTED = "detected"
        private const val EVENT_PROGRESS = "progress"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "org.protonaosp.elmyra") return
        android.util.Log.i(TAG, "handleLoadPackage for ${lpparam.packageName}")

        val gestureDetectedClass = try {
            lpparam.classLoader.loadClass(
                "org.protonaosp.elmyra.proto.nano.ContextHubMessages\$GestureDetected"
            )
        } catch (e: ClassNotFoundException) {
            android.util.Log.e(TAG, "GestureDetected class not found", e)
            return
        }

        try {
            XposedHelpers.findAndHookMethod(
                "org.protonaosp.elmyra.ElmyraService",
                lpparam.classLoader,
                "onGestureDetected",
                gestureDetectedClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        android.util.Log.i(TAG, "onGestureDetected callback")
                        val service = param.thisObject as Context
                        sendSqueezeBroadcast(service, EVENT_DETECTED)
                    }
                }
            )
            android.util.Log.i(TAG, "Hooked onGestureDetected")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to hook onGestureDetected", t)
            return
        }

        val gestureProgressClass = try {
            lpparam.classLoader.loadClass(
                "org.protonaosp.elmyra.proto.nano.ContextHubMessages\$GestureProgress"
            )
        } catch (e: ClassNotFoundException) {
            android.util.Log.e(TAG, "GestureProgress class not found", e)
            return
        }

        try {
            XposedHelpers.findAndHookMethod(
                "org.protonaosp.elmyra.ElmyraService",
                lpparam.classLoader,
                "onGestureProgress",
                gestureProgressClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val service = param.thisObject as Context
                        val progress = XposedHelpers.getFloatField(param.args[0], "progress")
                        sendSqueezeBroadcast(service, EVENT_PROGRESS, progress)
                    }
                }
            )
            android.util.Log.i(TAG, "Hooked onGestureProgress")
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "Failed to hook onGestureProgress", t)
        }
    }

    private fun sendSqueezeBroadcast(
        service: Context,
        eventType: String,
        progress: Float? = null
    ) {
        val intent = Intent(ACTION_SQUEEZE_DETECTED).apply {
            setPackage(LifeloggerPackage)
            putExtra(EXTRA_EVENT_TYPE, eventType)
            if (progress != null) {
                putExtra(EXTRA_PROGRESS, progress)
            }
        }
        service.sendBroadcast(intent)
        android.util.Log.i(TAG, "Sent squeeze broadcast eventType=$eventType progress=$progress")
    }
}
