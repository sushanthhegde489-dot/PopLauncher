package com.sushanth.poplauncher.tiles

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sushanth.poplauncher.R
import com.sushanth.poplauncher.data.repository.SettingsRepository

class WallpaperTileService : TileService() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentShowWallpaper = settingsRepository.settings.value.showWallpaper
        settingsRepository.setShowWallpaper(!currentShowWallpaper)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isShowing = settingsRepository.settings.value.showWallpaper
        tile.state = if (isShowing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_wallpaper_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isShowing) "On" else "Off"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_wallpaper)
        tile.updateTile()
    }
}
