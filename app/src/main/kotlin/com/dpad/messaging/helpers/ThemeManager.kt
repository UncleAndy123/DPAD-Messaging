package com.dpad.messaging.helpers

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.dpad.messaging.R

object ThemeManager {

    fun activityThemeRes(): Int {
        return when (Prefs.get().appAccent) {
            Prefs.ACCENT_GREEN -> R.style.Theme_DpadMessaging_Green
            Prefs.ACCENT_ORANGE -> R.style.Theme_DpadMessaging_Orange
            Prefs.ACCENT_ROSE -> R.style.Theme_DpadMessaging_Rose
            else -> R.style.Theme_DpadMessaging_Blue
        }
    }

    fun applyThemeMode(mode: String) {
        val nightMode = when (mode) {
            Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    fun applyAccentColor(activity: Activity) {
        val accentColor = accentColor(activity)

        activity.window.apply {
            statusBarColor = accentColor
            navigationBarColor = accentColor
        }

        activity.findViewById<View>(android.R.id.content)?.apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorBackground))
        }
    }

    @ColorInt
    fun accentColor(context: Context): Int {
        val colorRes = when (Prefs.get().appAccent) {
            Prefs.ACCENT_GREEN -> R.color.accent_green
            Prefs.ACCENT_ORANGE -> R.color.accent_orange
            Prefs.ACCENT_ROSE -> R.color.accent_rose
            else -> R.color.accent_blue
        }
        return ContextCompat.getColor(context, colorRes)
    }

    fun popupMenuContext(context: Context): Context =
        ContextThemeWrapper(context, R.style.ThemeOverlay_DpadMessaging_PopupMenu)
}
