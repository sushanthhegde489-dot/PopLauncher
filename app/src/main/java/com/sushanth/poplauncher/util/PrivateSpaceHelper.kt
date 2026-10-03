package com.sushanth.poplauncher.util

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

sealed interface PrivateSpaceResult {
    data object PromptedUnlock : PrivateSpaceResult
    data class Apps(val activities: List<LauncherActivityInfo>, val profile: UserHandle) : PrivateSpaceResult
    data class EmptyUnlocked(val profile: UserHandle) : PrivateSpaceResult
    data object LaunchedOem : PrivateSpaceResult
    data object Unavailable : PrivateSpaceResult
}

object PrivateSpaceHelper {

    private const val TAG = "PrivateSpaceHelper"

    /**
     * Determines whether a given UserHandle corresponds to an Android 15 Private Profile.
     */
    fun isPrivateProfile(userManager: UserManager?, profile: UserHandle?): Boolean {
        if (userManager == null || profile == null) return false
        try {
            val getUserPropertiesMethod = userManager.javaClass.getMethod("getUserProperties", UserHandle::class.java)
            val userProperties = getUserPropertiesMethod.invoke(userManager, profile)
            if (userProperties != null) {
                val isPrivateMethod = runCatching { userProperties.javaClass.getMethod("isPrivateProfile") }.getOrNull()
                if (isPrivateMethod != null) {
                    val isPrivate = isPrivateMethod.invoke(userProperties) as? Boolean
                    if (isPrivate == true) return true
                }
                val getProfileTypeMethod = runCatching { userProperties.javaClass.getMethod("getProfileType") }.getOrNull()
                if (getProfileTypeMethod != null) {
                    val profileType = getProfileTypeMethod.invoke(userProperties) as? String
                    if (profileType?.contains("private", ignoreCase = true) == true) return true
                }
            }
        } catch (_: Throwable) {}

        if (profile.toString().contains("private", ignoreCase = true)) return true

        return false
    }

    /**
     * Finds the Android 15 Private Profile UserHandle if present.
     */
    fun findPrivateProfile(context: Context): UserHandle? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return null

        val allProfiles = mutableListOf<UserHandle>()
        launcherApps?.profiles?.let { allProfiles.addAll(it) }
        userManager.userProfiles?.let { allProfiles.addAll(it) }

        for (profile in allProfiles.distinct()) {
            if (isPrivateProfile(userManager, profile)) {
                return profile
            }
        }
        return null
    }

    /**
     * Inspects the current state of Private Space (Android 15 or OEM Vault)
     * and attempts to unlock or resolve it.
     */
    fun resolvePrivateSpace(context: Context): PrivateSpaceResult {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager

        // 1. Android 15 (API 35+) Private Profile
        if (userManager != null && launcherApps != null) {
            val privateProfile = findPrivateProfile(context)
            if (privateProfile != null) {
                if (userManager.isQuietModeEnabled(privateProfile)) {
                    val prompted = requestQuietModeUnlock(launcherApps, userManager, privateProfile)
                    if (prompted) {
                        return PrivateSpaceResult.PromptedUnlock
                    }
                } else {
                    val activities = try {
                        launcherApps.getActivityList(null, privateProfile)
                    } catch (_: Exception) {
                        emptyList<LauncherActivityInfo>()
                    }
                    return if (activities.isNotEmpty()) {
                        PrivateSpaceResult.Apps(activities, privateProfile)
                    } else {
                        PrivateSpaceResult.EmptyUnlocked(privateProfile)
                    }
                }
            }
        }

        // 2. OEM Secure Folder, Private Safe, Second Space, and Hidden Apps
        val oemIntents = listOfNotNull(
            // Samsung Secure Folder
            Intent("com.samsung.knox.securefolder.action.LAUNCH_SECURE_FOLDER"),
            Intent("knox.securefolder.open"),
            context.packageManager.getLaunchIntentForPackage("com.samsung.knox.securefolder"),
            context.packageManager.getLaunchIntentForPackage("com.sec.knox.foldercontainer"),

            // OnePlus, Oppo, Realme Private Safe & Hidden Apps
            Intent("com.oplus.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.encryption.ACTION_OPEN_ENCRYPTION"),
            Intent("com.oplus.encryption.ACTION_OPEN_ENCRYPTION"),
            context.packageManager.getLaunchIntentForPackage("com.coloros.privatesafe"),
            context.packageManager.getLaunchIntentForPackage("com.oplus.encryption"),

            // Xiaomi / MIUI / HyperOS Second Space & Hidden Apps
            Intent("miui.intent.action.VIEW_HIDDEN_APPS"),
            Intent("com.miui.securityadd.action.ENTER_SECOND_SPACE"),
            context.packageManager.getLaunchIntentForPackage("com.miui.securitycore"),
            context.packageManager.getLaunchIntentForPackage("com.miui.securitycenter"),

            // Huawei / Honor PrivateSpace
            Intent("com.huawei.privatespace.action.OPEN"),

            // Vivo / iQOO Privacy Space / App Encryption
            Intent("com.iqoo.secure.action.PRIVATE_SPACE"),
            context.packageManager.getLaunchIntentForPackage("com.iqoo.secure"),

            // Motorola / Lenovo Secure Folder
            context.packageManager.getLaunchIntentForPackage("com.motorola.securefolder")
        )

        for (intent in oemIntents) {
            if (canResolve(context, intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return PrivateSpaceResult.LaunchedOem
                } catch (e: Exception) {
                    Log.w(TAG, "Failed launching OEM intent: ${intent.action}", e)
                }
            }
        }

        // Strict: NEVER fallback to Android System Settings!
        return PrivateSpaceResult.Unavailable
    }

    /**
     * Attempts to unlock or open Private Space.
     * Returns true if unlock was prompted, OEM vault was launched, or private apps were launched.
     * Returns false if unavailable.
     */
    fun openPrivateSpace(context: Context): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        return when (val result = resolvePrivateSpace(context)) {
            is PrivateSpaceResult.PromptedUnlock -> true
            is PrivateSpaceResult.LaunchedOem -> true
            is PrivateSpaceResult.EmptyUnlocked -> true
            is PrivateSpaceResult.Apps -> {
                try {
                    launcherApps?.startMainActivity(result.activities.first().componentName, result.profile, null, null)
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed launching private space app", e)
                    false
                }
            }
            is PrivateSpaceResult.Unavailable -> false
        }
    }

    private fun requestQuietModeUnlock(launcherApps: LauncherApps, userManager: UserManager, profile: UserHandle): Boolean {
        return try {
            val umMethod = userManager.javaClass.getMethod(
                "requestQuietModeEnabled",
                Boolean::class.javaPrimitiveType,
                UserHandle::class.java
            )
            umMethod.invoke(userManager, false, profile)
            true
        } catch (_: Throwable) {
            try {
                val method = launcherApps.javaClass.getMethod(
                    "requestQuietModeEnabled",
                    Boolean::class.javaPrimitiveType,
                    UserHandle::class.java
                )
                method.invoke(launcherApps, false, profile)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun canResolve(context: Context, intent: Intent): Boolean {
        return try {
            val resolve = context.packageManager.resolveActivity(intent, 0)
                ?: context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolve != null) return true
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
