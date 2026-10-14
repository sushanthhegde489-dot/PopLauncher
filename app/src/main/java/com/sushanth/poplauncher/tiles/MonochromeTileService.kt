package com.sushanth.poplauncher.tiles

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sushanth.poplauncher.R
import com.sushanth.poplauncher.data.repository.SettingsRepository

class MonochromeTileService : TileService() {

    private lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isMono = settingsRepository.settings.value.themePalette.isMonochrome
        settingsRepository.setMonochromeMode(!isMono)
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isMono = settingsRepository.settings.value.themePalette.isMonochrome
        tile.state = if (isMono) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_monochrome_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isMono) "On" else "Off"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_theme)
        tile.updateTile()
    }
}
