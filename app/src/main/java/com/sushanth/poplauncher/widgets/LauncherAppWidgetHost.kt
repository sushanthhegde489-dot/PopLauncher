package com.sushanth.poplauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.TextView

class LauncherAppWidgetHost(
    context: Context,
    hostId: Int = APPWIDGET_HOST_ID
) : AppWidgetHost(context, hostId) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return try {
            super.onCreateView(context, appWidgetId, appWidget).apply {
                setPadding(0, 0, 0, 0)
            }
        } catch (e: Exception) {
            // Provide a graceful fallback to prevent third-party widget crashes from taking down the launcher
            ErrorAppWidgetHostView(context)
        }
    }

    private class ErrorAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
        @android.annotation.SuppressLint("SetTextI18n")
        override fun getErrorView(): View {
            return TextView(context).apply {
                text = "Widget Error"
                setTextColor(Color.RED)
                setBackgroundColor(Color.BLACK)
            }
        }
    }

    companion object {
        const val APPWIDGET_HOST_ID = 1024
    }
}
