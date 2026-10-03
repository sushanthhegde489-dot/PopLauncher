package com.sushanth.poplauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

object PrivateSpaceHelper {

    private const val TAG = "PrivateSpaceHelper"

    /**
     * Attempts to unlock or open Android default Private Space.
     * Uses Android 15 (API 35+) Private Profile official APIs when available,
     * or OEM Private Safe / Secure Folder / Private Space intents.
     *
     * IMPORTANT: Under no circumstances does this open Android System Settings.
     */
    fun openPrivateSpace(context: Context): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager

        // 1. Android 15 (API 35+) Private Profile
        if (Build.VERSION.SDK_INT >= 35 && userManager != null && launcherApps != null) {
            try {
                val profiles = launcherApps.profiles
                val privateProfile = findPrivateProfile(userManager, profiles)
                if (privateProfile != null) {
                    if (userManager.isQuietModeEnabled(privateProfile)) {
                        // Request Android system unlock prompt (fingerprint / PIN / pattern)
                        val prompted = requestQuietModeUnlock(launcherApps, userManager, privateProfile)
                        if (prompted) return true
                    } else {
                        // Already unlocked! Launch private profile's primary activity
                        val privateApps = try {
                            launcherApps.getActivityList(null, privateProfile)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (privateApps.isNotEmpty()) {
                            try {
                                launcherApps.startMainActivity(privateApps.first().componentName, privateProfile, null, null)
                                return true
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to start private profile main activity", e)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error checking Android 15 private profile", e)
            }
        }

        // 2. OEM Hidden Apps, Secure Folder, and Private Safe Intents
        val oemIntents = listOfNotNull(
            // Samsung Secure Folder
            Intent("com.samsung.knox.securefolder.action.LAUNCH_SECURE_FOLDER"),
            Intent("knox.securefolder.open"),
            context.packageManager.getLaunchIntentForPackage("com.samsung.knox.securefolder"),

            // OnePlus, Oppo, Realme Private Safe & Hidden Apps
            Intent("com.oplus.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.encryption.ACTION_OPEN_ENCRYPTION"),
            Intent("com.oplus.encryption.ACTION_OPEN_ENCRYPTION"),
            context.packageManager.getLaunchIntentForPackage("com.coloros.privatesafe"),

            // Xiaomi / MIUI / HyperOS Second Space & Hidden Apps
            Intent("miui.intent.action.VIEW_HIDDEN_APPS"),
            Intent("com.miui.securityadd.action.ENTER_SECOND_SPACE"),
            context.packageManager.getLaunchIntentForPackage("com.miui.securitycore"),

            // Huawei / Honor PrivateSpace
            Intent("com.huawei.privatespace.action.OPEN"),

            // Vivo / iQOO Privacy Space / App Encryption
            Intent("com.iqoo.secure.action.PRIVATE_SPACE")
        )

        for (intent in oemIntents) {
            if (canResolve(context, intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed launching OEM intent: ${intent.action}", e)
                }
            }
        }

        // Strict: NEVER fallback to Android System Settings!
        return false
    }

    private fun requestQuietModeUnlock(launcherApps: LauncherApps, userManager: UserManager, profile: UserHandle): Boolean {
        try {
            val method = launcherApps.javaClass.getMethod(
                "requestQuietModeEnabled",
                Boolean::class.javaPrimitiveType,
                UserHandle::class.java
            )
            method.invoke(launcherApps, false, profile)
            return true
        } catch (_: Throwable) {
            return try {
                val umMethod = userManager.javaClass.getMethod(
                    "requestQuietModeEnabled",
                    Boolean::class.javaPrimitiveType,
                    UserHandle::class.java
                )
                umMethod.invoke(userManager, false, profile)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun findPrivateProfile(userManager: UserManager, profiles: List<UserHandle>): UserHandle? {
        for (profile in profiles) {
            try {
                // In API 35: userManager.getUserProperties(profile).isPrivateProfile
                val getUserPropertiesMethod = userManager.javaClass.getMethod("getUserProperties", UserHandle::class.java)
                val userProperties = getUserPropertiesMethod.invoke(userManager, profile)
                if (userProperties != null) {
                    val isPrivateMethod = runCatching {
                        userProperties.javaClass.getMethod("isPrivateProfile")
                    }.getOrNull()
                    if (isPrivateMethod != null) {
                        val isPrivate = isPrivateMethod.invoke(userProperties) as? Boolean
                        if (isPrivate == true) return profile
                    }
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun canResolve(context: Context, intent: Intent): Boolean {
        return try {
            val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolve != null
        } catch (_: Exception) {
            false
        }
    }
}
