package com.example.prefixblocker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BlockLogStore {
    private const val PREFS = "prefix_blocker"
    private const val KEY_LOG = "block_log"
    private const val MAX_ENTRIES = 100

    fun log(ctx: Context, number: String, prefix: String) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
        val entry = "$time  $number  (matched $prefix)"
        val updated = (p.getStringSet(KEY_LOG, emptySet()) ?: emptySet()).toMutableList()
        updated.add(0, entry)
        prefs_trimmed(p, updated.take(MAX_ENTRIES).toSet())
    }

    private fun prefs_trimmed(
        p: android.content.SharedPreferences,
        value: Set<String>
    ) {
        p.edit().putStringSet(KEY_LOG, value).apply()
    }

    fun entries(ctx: Context): List<String> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return (p.getStringSet(KEY_LOG, emptySet()) ?: emptySet()).sortedDescending()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_LOG).apply()
    }
}
