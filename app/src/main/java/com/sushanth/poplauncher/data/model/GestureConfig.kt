package com.sushanth.poplauncher.data.model

enum class GestureType(val label: String, val description: String) {
    DOUBLE_TAP("Double Tap", "Double tap on empty space"),
    TRIPLE_TAP("Triple Tap", "Triple tap on empty space"),
    DOUBLE_TAP_SWIPE_UP("Double Tap & Swipe Up", "Double tap then swipe upward"),
    DOUBLE_TAP_SWIPE_DOWN("Double Tap & Swipe Down", "Double tap then swipe downward"),
    SWIPE_DOWN("Swipe Down", "Swipe downward with one finger"),
    SWIPE_UP("Swipe Up", "Swipe upward with one finger"),
    TWO_FINGER_SWIPE_DOWN("Two-Finger Swipe Down", "Swipe downward with two fingers"),
    TWO_FINGER_SWIPE_UP("Two-Finger Swipe Up", "Swipe upward with two fingers"),
    PINCH_IN("Pinch In", "Pinch inward with two fingers"),
    PINCH_OUT("Pinch Out", "Spread outward with two fingers"),
    TWO_FINGER_TAP("Two-Finger Tap", "Tap with two fingers simultaneously");

    val displayName: String get() = label
}

enum class GestureActionType(val label: String) {
    NONE("None"),
    OPEN_APP("Open App"),
    OPEN_APP_GROUP("Open App Group"),
    OPEN_SEARCH("Open Search"),
    OPEN_SETTINGS("Open Settings"),
    OPEN_NOTIFICATION_SHADE("Open Notifications"),
    @Deprecated("Use OPEN_NOTIFICATION_SHADE instead", ReplaceWith("OPEN_NOTIFICATION_SHADE"))
    OPEN_NOTIFICATIONS("Open Notifications"),
    OPEN_PRIVATE_SPACE("Open Private Space"),
    OPEN_HIDDEN_APPS("Open Hidden Apps"),
    LOCK_SCREEN("Lock Screen")
}

data class GestureConfig(
    val actionType: GestureActionType = GestureActionType.NONE,
    val targetPackage: String? = null,
    val targetLabel: String? = null
) {
    fun toSerialized(): String =
        "${actionType.name}|${targetPackage.orEmpty()}|${targetLabel.orEmpty()}"

    companion object {
        fun fromSerialized(raw: String?): GestureConfig {
            if (raw.isNullOrBlank()) return GestureConfig()
            val parts = raw.split("|")
            val rawType = runCatching { GestureActionType.valueOf(parts[0]) }
                .getOrDefault(GestureActionType.NONE)
            @Suppress("DEPRECATION")
            val type = if (rawType == GestureActionType.OPEN_NOTIFICATIONS) {
                GestureActionType.OPEN_NOTIFICATION_SHADE
            } else {
                rawType
            }
            val pkg = parts.getOrNull(1)?.ifBlank { null }
            val label = parts.getOrNull(2)?.ifBlank { null }
            return GestureConfig(
                actionType = type,
                targetPackage = pkg,
                targetLabel = label
            )
        }
    }
}
