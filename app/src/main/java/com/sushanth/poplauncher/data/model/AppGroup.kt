package com.sushanth.poplauncher.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AppGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val packageNames: Set<String> = emptySet()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("packageNames", JSONArray(packageNames.toList()))
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AppGroup {
            val id = json.optString("id", UUID.randomUUID().toString())
            val name = json.optString("name", "Group")
            val packages = mutableSetOf<String>()
            val arr = json.optJSONArray("packageNames")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val pkg = arr.optString(i)
                    if (pkg.isNotBlank()) {
                        packages.add(pkg)
                    }
                }
            }
            return AppGroup(id = id, name = name, packageNames = packages)
        }
    }
}
