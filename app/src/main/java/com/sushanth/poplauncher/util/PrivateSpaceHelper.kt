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
     * Attempts to open the Android default Private Space screen / container.
     * Uses Android 15 (API 35+) system private space screen intents,
     * or OEM Private Safe / Secure Folder / Second Space / Private Space screens.
     */
    fun openPrivateSpace(context: Context): Boolean {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager

        // 1. If profile is locked in quiet mode, prompt system biometric / PIN unlock
        if (userManager != null && launcherApps != null) {
            try {
                val allProfiles = mutableListOf<UserHandle>()
                try {
                    allProfiles.addAll(launcherApps.profiles)
                } catch (_: Throwable) {}
                try {
                    userManager.userProfiles?.let { allProfiles.addAll(it) }
                } catch (_: Throwable) {}

                val privateProfile = findPrivateProfile(context, userManager, launcherApps, allProfiles.distinct())
                if (privateProfile != null) {
                    val isQuiet = try {
                        userManager.isQuietModeEnabled(privateProfile)
                    } catch (_: Throwable) {
                        false
                    }
                    if (isQuiet) {
                        val prompted = requestQuietModeUnlock(userManager, privateProfile)
                        if (prompted) {
                            android.widget.Toast.makeText(context, "Unlocking Private Space...", android.widget.Toast.LENGTH_SHORT).show()
                            return true
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error checking Android 15 private profile state", e)
            }
        }

        // 2. Android 15 System Private Space Screen / Authentication / Settings Activity
        val systemPrivateIntents = listOfNotNull(
            Intent("android.settings.PRIVATE_SPACE_SETTINGS"),
            Intent("com.android.settings.action.PRIVATE_SPACE_SETTINGS"),
            Intent("com.android.settings.action.OPEN_PRIVATE_SPACE_SETTINGS"),
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.privatespace.PrivateSpaceAuthenticationActivity")
            },
            Intent().apply {
                setClassName("com.android.settings", "com.android.settings.privatespace.PrivateProfileSettings")
            }
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

    private const val PREFS_NAME = "private_space_helper_prefs"
    private const val KEY_PRIVATE_PROFILE_USER_ID = "private_profile_user_id"

    private fun getSavedPrivateProfileId(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(KEY_PRIVATE_PROFILE_USER_ID)) {
            val id = prefs.getInt(KEY_PRIVATE_PROFILE_USER_ID, -1)
            if (id >= 0) id else null
        } else null
    }

    private fun savePrivateProfileId(context: Context, id: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_PRIVATE_PROFILE_USER_ID, id)
            .apply()
    }

    fun openAddAppsToPrivateSpace(context: Context): Boolean {
        val privateProfile = getPrivateProfile(context) ?: return false
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps

        // 1. Try launching Google Play Store inside private profile
        if (launcherApps != null) {
            val playStoreActivities = try {
                launcherApps.getActivityList("com.android.vending", privateProfile)
            } catch (_: Exception) {
                emptyList()
            }
            if (playStoreActivities.isNotEmpty()) {
                try {
                    launcherApps.startMainActivity(playStoreActivities.first().componentName, privateProfile, null, null)
                    android.widget.Toast.makeText(context, "Opening Play Store in Private Space...", android.widget.Toast.LENGTH_SHORT).show()
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed launching Play Store in private profile", e)
                }
            }
        }

        // 2. Try Samsung Knox / OEM Add Apps Intent
        val oemAddIntents = listOf(
            Intent("com.samsung.knox.securefolder.action.ADD_APPS"),
            Intent("com.oplus.safe.action.ADD_APPS"),
            Intent("com.android.settings.action.OPEN_PRIVATE_SPACE_SETTINGS")
        )
        for (intent in oemAddIntents) {
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

    fun isPrivateProfile(context: Context, profile: UserHandle): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        return isPrivateProfile(userManager, profile, context)
    }

    fun isPrivateProfile(userManager: UserManager?, profile: UserHandle, context: Context? = null): Boolean {
        if (userManager == null) return false
        if (profile == android.os.Process.myUserHandle()) return false

        val userId = try {
            profile.javaClass.getMethod("getIdentifier").invoke(profile) as? Int
        } catch (_: Throwable) {
            null
        } ?: profile.hashCode()

        // 0. If previously identified and saved as Private Space, keep it permanently identified
        if (context != null) {
            val savedId = getSavedPrivateProfileId(context)
            if (savedId != null && savedId == userId) {
                return true
            }
        }

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
                                savePrivateProfileId(context, userId)
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
                    if (context != null) savePrivateProfileId(context, userId)
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
                    if (isPrivate == true) {
                        if (context != null) savePrivateProfileId(context, userId)
                        return true
                    }
                }
                if (userProperties.toString().contains("isPrivateProfile=true", ignoreCase = true)) {
                    if (context != null) savePrivateProfileId(context, userId)
                    return true
                }
            }
        } catch (_: Throwable) {}

        // 4. Check getUserInfo(id)
        try {
            val getUserInfoMethod = userManager.javaClass.getMethod("getUserInfo", Int::class.javaPrimitiveType)
            val userInfo = getUserInfoMethod.invoke(userManager, userId)
            if (userInfo != null) {
                val userTypeField = runCatching { userInfo.javaClass.getField("userType").get(userInfo) as? String }.getOrNull()
                if (userTypeField != null) {
                    if (userTypeField == "android.os.usertype.profile.PRIVATE" || userTypeField.contains("private", ignoreCase = true)) {
                        if (context != null) savePrivateProfileId(context, userId)
                        return true
                    }
                    if (userTypeField.contains("clone", ignoreCase = true) || userTypeField.contains("managed", ignoreCase = true) || userTypeField.contains("work", ignoreCase = true)) {
                        return false
                    }
                }
                val isPrivateField = runCatching { userInfo.javaClass.getField("isPrivateProfile").getBoolean(userInfo) }.getOrNull()
                if (isPrivateField == true) {
                    if (context != null) savePrivateProfileId(context, userId)
                    return true
                }
            }
        } catch (_: Throwable) {}

        // 5. Check if profile string representation explicitly contains "private"
        if (profileStr.contains("private")) {
            if (context != null) savePrivateProfileId(context, userId)
            return true
        }

        // 6. Check quiet mode + not managed (work) profile
        try {
            val isQuiet = userManager.isQuietModeEnabled(profile)
            val isManaged = try {
                val method = userManager.javaClass.getMethod("isManagedProfile", Int::class.javaPrimitiveType)
                method.invoke(userManager, userId) as? Boolean ?: false
            } catch (_: Throwable) {
                false
            }
            if (isQuiet && !isManaged) {
                if (context != null) savePrivateProfileId(context, userId)
                return true
            }
        } catch (_: Throwable) {}

        // 7. Android 15 fallback: Non-managed, non-clone profile is Private Space
        val isManaged = try {
            val method = userManager.javaClass.getMethod("isManagedProfile", Int::class.javaPrimitiveType)
            method.invoke(userManager, userId) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
        if (!isManaged && (Build.VERSION.SDK_INT >= 35 || userManager.userProfiles?.size ?: 0 <= 2)) {
            if (context != null) savePrivateProfileId(context, userId)
            return true
        }

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
