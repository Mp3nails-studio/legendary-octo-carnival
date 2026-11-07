package com.controlcenter.app

import android.content.Context

/** Proste ustawienia zapisywane w SharedPreferences. */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("cc_prefs", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    /** Szerokosc kazdej strefy wyzwalania w % szerokosci ekranu (lewa = powiadomienia, prawa = ustawienia). */
    fun triggerWidth(c: Context) = sp(c).getInt("trigger_width", 40).coerceIn(15, 50)
    fun setTriggerWidth(c: Context, v: Int) = sp(c).edit().putInt("trigger_width", v).apply()

    /** Wysokosc strefy wyzwalania w dp. */
    fun triggerHeight(c: Context) = sp(c).getInt("trigger_height", 28)
    fun setTriggerHeight(c: Context, v: Int) = sp(c).edit().putInt("trigger_height", v).apply()

    /** Odstep strefy od gory ekranu w dp (zeby nie nachodzila na systemowy pasek stanu). */
    fun triggerOffset(c: Context) = sp(c).getInt("trigger_offset", 28)
    fun setTriggerOffset(c: Context, v: Int) = sp(c).edit().putInt("trigger_offset", v).apply()

    /** Sila rozmycia tla za panelem (Android 12+). */
    fun blur(c: Context) = sp(c).getInt("blur", 80)
    fun setBlur(c: Context, v: Int) = sp(c).edit().putInt("blur", v).apply()

    /** Pokazuje strefe wyzwalania jako czerwony pasek (do ustawiania). */
    fun showTrigger(c: Context) = sp(c).getBoolean("show_trigger", false)
    fun setShowTrigger(c: Context, v: Boolean) = sp(c).edit().putBoolean("show_trigger", v).apply()
}
