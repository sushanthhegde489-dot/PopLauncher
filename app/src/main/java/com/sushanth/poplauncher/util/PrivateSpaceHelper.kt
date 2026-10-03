package com.sushanth.poplauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.util.Log

object PrivateSpaceHelper {

    private const val TAG = "PrivateSpaceHelper"

    /**
     * Attempts to unlock or open Android default Private Space.
     * Uses Android 15 (API 35+) Private Profile official APIs when available,
     * or OEM Private Safe / Secure Folder / Private Space intents.
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
                        try {
                            val method = launcherApps.javaClass.getMethod(
                                "requestQuietModeEnabled",
                                Boolean::class.javaPrimitiveType,
                                UserHandle::class.java
                            )
                            method.invoke(launcherApps, false, privateProfile)
                            return true
                        } catch (_: Throwable) {
                            val umMethod = userManager.javaClass.getMethod(
                                "requestQuietModeEnabled",
                                Boolean::class.javaPrimitiveType,
                                UserHandle::class.java
                            )
                            umMethod.invoke(userManager, false, privateProfile)
                            return true
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error checking Android 15 private profile", e)
            }
        }

        // 2. Android 15 Private Space Settings Intent
        val privateSpaceSettingsIntent = Intent("android.settings.PRIVATE_SPACE_SETTINGS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (canResolve(context, privateSpaceSettingsIntent)) {
            try {
                context.startActivity(privateSpaceSettingsIntent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch PRIVATE_SPACE_SETTINGS", e)
            }
        }

        // 3. OEM Hidden Apps, Secure Folder, and Private Safe Intents
        val oemIntents = listOfNotNull(
            // Samsung Secure Folder
            context.packageManager.getLaunchIntentForPackage("com.samsung.knox.securefolder"),
            Intent("knox.securefolder.open"),
            Intent("com.samsung.knox.securefolder.action.LAUNCH_SECURE_FOLDER"),

            // OnePlus, Oppo, Realme Private Safe & Hidden Apps
            context.packageManager.getLaunchIntentForPackage("com.coloros.privatesafe"),
            Intent("com.oplus.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.encryption.ACTION_OPEN_ENCRYPTION"),
            Intent("com.oplus.encryption.ACTION_OPEN_ENCRYPTION"),

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

        // 4. Default Android Settings fallback (Privacy or Security & Privacy)
        val fallbackIntents = listOf(
            Intent(Settings.ACTION_PRIVACY_SETTINGS),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in fallbackIntents) {
            if (canResolve(context, intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {}
            }
        }

        return false
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
