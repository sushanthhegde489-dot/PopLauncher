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

        // 1. Android 15 Private Profile Discovery
        if (userManager != null && launcherApps != null) {
            try {
                val allProfiles = mutableListOf<UserHandle>()
                try {
                    allProfiles.addAll(launcherApps.profiles)
                } catch (_: Throwable) {}
                try {
                    userManager.userProfiles?.let { allProfiles.addAll(it) }
                } catch (_: Throwable) {}

                val distinctProfiles = allProfiles.distinct()
                val privateProfile = findPrivateProfile(context, userManager, launcherApps, distinctProfiles)

                if (privateProfile != null) {
                    val isQuiet = try {
                        userManager.isQuietModeEnabled(privateProfile)
                    } catch (_: Throwable) {
                        false
                    }

                    if (isQuiet) {
                        // Request Android system unlock prompt (fingerprint / PIN / pattern)
                        val prompted = requestQuietModeUnlock(userManager, privateProfile)
                        if (prompted) {
                            android.widget.Toast.makeText(context, "Unlocking Private Space...", android.widget.Toast.LENGTH_SHORT).show()
                            return true
                        }
                    } else {
                        // Already unlocked! Caller should display Private Space bubbles.
                        return true
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error checking Android 15 private profile", e)
            }
        }

        // 2. Android 15 System Private Space Gatekeeper / Authentication Activity
        val systemPrivateIntents = listOfNotNull(
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.privatespace.PrivateSpaceAuthenticationActivity")
            },
            Intent("com.android.settings.action.OPEN_PRIVATE_SPACE_SETTINGS")
        )
        for (intent in systemPrivateIntents) {
            if (canResolve(context, intent)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed launching system private intent: ${intent.component}", e)
                }
            }
        }

        // 3. OEM Hidden Apps, Secure Folder, and Private Safe Intents
        val oemIntents = listOfNotNull(
            // Samsung Secure Folder
            Intent("com.samsung.knox.securefolder.action.LAUNCH_SECURE_FOLDER"),
            Intent("knox.securefolder.open"),
            context.packageManager.getLaunchIntentForPackage("com.samsung.knox.securefolder"),
            Intent().setClassName("com.samsung.knox.securefolder", "com.samsung.knox.securefolder.switcher.SwitchToFolderActivity"),
            Intent().setClassName("com.samsung.knox.securefolder", "com.samsung.knox.securefolder.presentation.switcher.SwitchToFolderActivity"),

            // OnePlus, Oppo, Realme Private Safe & Hidden Apps
            Intent("com.oplus.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.safe.action.OPEN_PRIVATE_SAFE"),
            Intent("com.coloros.encryption.ACTION_OPEN_ENCRYPTION"),
            Intent("com.oplus.encryption.ACTION_OPEN_ENCRYPTION"),
            context.packageManager.getLaunchIntentForPackage("com.coloros.privatesafe"),
            context.packageManager.getLaunchIntentForPackage("com.oplus.privatesafe"),
            Intent().setClassName("com.coloros.safecenter", "com.coloros.privatesafe.activity.PrivateSafeActivity"),
            Intent().setClassName("com.oplus.safecenter", "com.oplus.privatesafe.activity.PrivateSafeActivity"),

            // Xiaomi / MIUI / HyperOS Second Space & Hidden Apps
            Intent("miui.intent.action.VIEW_HIDDEN_APPS"),
            Intent("com.miui.securityadd.action.ENTER_SECOND_SPACE"),
            Intent("miui.intent.action.HIDDEN_APPS_ACTIVITY"),
            context.packageManager.getLaunchIntentForPackage("com.miui.securitycenter"),
            context.packageManager.getLaunchIntentForPackage("com.miui.securitycore"),
            Intent().setClassName("com.miui.securitycenter", "com.miui.secondspace.ui.activity.SecondSpaceSettingActivity"),

            // Huawei / Honor PrivateSpace
            Intent("com.huawei.privatespace.action.OPEN"),
            context.packageManager.getLaunchIntentForPackage("com.huawei.privatespace"),

            // Vivo / iQOO Privacy Space / App Encryption
            Intent("com.iqoo.secure.action.PRIVATE_SPACE"),
            Intent("com.vivo.permissionmanager.action.PURVIEW_ACTIVITY"),
            context.packageManager.getLaunchIntentForPackage("com.iqoo.secure")
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

    fun getPrivateProfile(context: Context): UserHandle? {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return null
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return null
        val allProfiles = mutableListOf<UserHandle>()
        try {
            allProfiles.addAll(launcherApps.profiles)
        } catch (_: Throwable) {}
        try {
            userManager.userProfiles?.let { allProfiles.addAll(it) }
        } catch (_: Throwable) {}
        return findPrivateProfile(context, userManager, launcherApps, allProfiles.distinct())
    }

    fun requestQuietModeUnlock(userManager: UserManager, profile: UserHandle): Boolean {
        return try {
            val umMethod = userManager.javaClass.getMethod(
                "requestQuietModeEnabled",
                Boolean::class.javaPrimitiveType,
                UserHandle::class.java
            )
            umMethod.invoke(userManager, false, profile)
            // Even if the method returns false (which indicates the system unlock prompt
            // is actively being presented to the user), the invocation succeeded!
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Failed calling requestQuietModeEnabled", e)
            false
        }
    }

    fun isPrivateProfile(context: Context, profile: UserHandle): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        return isPrivateProfile(userManager, profile, context)
    }

    fun isPrivateProfile(userManager: UserManager?, profile: UserHandle, context: Context? = null): Boolean {
        if (userManager == null) return false
        if (profile == android.os.Process.myUserHandle()) return false

        val profileStr = profile.toString().lowercase()
        if (profileStr.contains("clone") || profileStr.contains("dual") || profileStr.contains("work") || profileStr.contains("managed")) {
            return false
        }

        // 1. Check LauncherApps.getLauncherUserInfo(profile)
        if (context != null) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (launcherApps != null) {
                try {
                    val getInfoMethod = launcherApps.javaClass.getMethod("getLauncherUserInfo", UserHandle::class.java)
                    val info = getInfoMethod.invoke(launcherApps, profile)
                    if (info != null) {
                        val getTypeMethod = info.javaClass.getMethod("getUserType")
                        val type = getTypeMethod.invoke(info) as? String
                        if (type != null) {
                            if (type == "android.os.usertype.profile.PRIVATE" || type.contains("private", ignoreCase = true)) {
                                return true
                            }
                            if (type.contains("clone", ignoreCase = true) || type.contains("managed", ignoreCase = true) || type.contains("work", ignoreCase = true)) {
                                return false
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }

        // 2. Check UserManager getProfileType(profile)
        try {
            val getProfileTypeMethod = userManager.javaClass.getMethod("getProfileType", UserHandle::class.java)
            val type = getProfileTypeMethod.invoke(userManager, profile) as? String
            if (type != null) {
                if (type == "android.os.usertype.profile.PRIVATE" || type.contains("private", ignoreCase = true)) {
                    return true
                }
                if (type.contains("clone", ignoreCase = true) || type.contains("managed", ignoreCase = true) || type.contains("work", ignoreCase = true)) {
                    return false
                }
            }
        } catch (_: Throwable) {}

        // 3. Check getUserProperties (if present on OEM/framework)
        try {
            val getUserPropertiesMethod = userManager.javaClass.getMethod("getUserProperties", UserHandle::class.java)
            val userProperties = getUserPropertiesMethod.invoke(userManager, profile)
            if (userProperties != null) {
                val isPrivateMethod = runCatching {
                    userProperties.javaClass.getMethod("isPrivateProfile")
                }.getOrNull()
                if (isPrivateMethod != null) {
                    val isPrivate = isPrivateMethod.invoke(userProperties) as? Boolean
                    if (isPrivate == true) return true
                }
                if (userProperties.toString().contains("isPrivateProfile=true", ignoreCase = true)) {
                    return true
                }
            }
        } catch (_: Throwable) {}

        // 4. Check getUserInfo(id)
        try {
            val idMethod = profile.javaClass.getMethod("getIdentifier")
            val id = idMethod.invoke(profile) as? Int ?: profile.hashCode()
            val getUserInfoMethod = userManager.javaClass.getMethod("getUserInfo", Int::class.javaPrimitiveType)
            val userInfo = getUserInfoMethod.invoke(userManager, id)
            if (userInfo != null) {
                val userTypeField = runCatching { userInfo.javaClass.getField("userType").get(userInfo) as? String }.getOrNull()
                if (userTypeField != null) {
                    if (userTypeField == "android.os.usertype.profile.PRIVATE" || userTypeField.contains("private", ignoreCase = true)) {
                        return true
                    }
                    if (userTypeField.contains("clone", ignoreCase = true) || userTypeField.contains("managed", ignoreCase = true) || userTypeField.contains("work", ignoreCase = true)) {
                        return false
                    }
                }
                val isPrivateField = runCatching { userInfo.javaClass.getField("isPrivateProfile").getBoolean(userInfo) }.getOrNull()
                if (isPrivateField == true) return true
            }
        } catch (_: Throwable) {}

        // 5. Check if profile string representation explicitly contains "private"
        if (profileStr.contains("private")) {
            return true
        }

        // 6. Check quiet mode + not managed (work) profile
        try {
            val isQuiet = userManager.isQuietModeEnabled(profile)
            val isManaged = try {
                val method = userManager.javaClass.getMethod("isManagedProfile", Int::class.javaPrimitiveType)
                method.invoke(userManager, profile.hashCode()) as? Boolean ?: false
            } catch (_: Throwable) {
                false
            }
            if (isQuiet && !isManaged) {
                return true
            }
        } catch (_: Throwable) {}

        return false
    }

    private fun findPrivateProfile(
        context: Context,
        userManager: UserManager,
        launcherApps: LauncherApps,
        profiles: List<UserHandle>
    ): UserHandle? {
        for (profile in profiles) {
            if (isPrivateProfile(userManager, profile, context)) return profile
        }
        return null
    }

    fun canResolve(context: Context, intent: Intent): Boolean {
        return try {
            val resolve = context.packageManager.resolveActivity(intent, 0)
            resolve != null
        } catch (_: Exception) {
            false
        }
    }
}
