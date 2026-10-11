package com.dpad.messaging.helpers

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
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
        activity.window.apply {
            statusBarColor = ContextCompat.getColor(activity, R.color.toolbarBg)
            navigationBarColor = ContextCompat.getColor(activity, R.color.surface_background)
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

    /** Accent-colored at rest, but always legible against the neutral focus fill. */
    fun focusAwareTint(context: Context): ColorStateList {
        return ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_focused),
                intArrayOf(android.R.attr.state_pressed),
                intArrayOf()
            ),
            intArrayOf(
                ContextCompat.getColor(context, R.color.focus_content),
                ContextCompat.getColor(context, R.color.focus_content),
                accentColor(context)
            )
        )
    }

    fun focusAwareTextColor(context: Context, restingColor: Int): ColorStateList {
        return ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
            intArrayOf(ContextCompat.getColor(context, R.color.focus_content), restingColor)
        )
    }

    fun popupMenuContext(context: Context): Context =
        ContextThemeWrapper(context, R.style.ThemeOverlay_DpadMessaging_PopupMenu)
}
