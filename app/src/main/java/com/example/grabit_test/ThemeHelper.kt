package com.example.grabitTest

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate

/**
 * 앱 테마 모드 저장 및 적용.
 * 기본값: 다크. 라이트/다크만 지원 (시스템 테마 미지원).
 */
object ThemeHelper {
    private const val PREFS_NAME = "grabit_theme"
    private const val KEY_THEME_MODE = "theme_mode"

    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    fun getThemeMode(context: android.content.Context): String {
        return context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, MODE_DARK) ?: MODE_DARK
    }

    fun setThemeMode(context: android.content.Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode)
            .apply()
    }

    fun isLightMode(context: android.content.Context): Boolean =
        getThemeMode(context) == MODE_LIGHT

    fun applyTheme(context: android.content.Context) {
        val mode = getThemeMode(context)
        val nightMode = if (mode == MODE_LIGHT) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }
}
