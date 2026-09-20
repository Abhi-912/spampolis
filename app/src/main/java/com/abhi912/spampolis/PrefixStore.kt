package com.abhi912.spampolis

import android.content.Context

object PrefixStore {
    private const val PREFS = "spampolis"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PREFIXES = "prefixes"

    // Indian telemarketing headers (140/160 series) as sensible defaults.
    private val DEFAULTS = setOf("140", "160")

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ENABLED, true)

    fun setEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getPrefixes(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_PREFIXES, DEFAULTS)?.toSet() ?: DEFAULTS

    fun addPrefix(ctx: Context, prefix: String): Boolean {
        val tokens = PhoneNormalize.patternTokens(prefix)
        // Must contain at least one digit or X (a lone "*" would block everything).
        if (tokens.isEmpty() || !tokens.any { it.isDigit() || it == 'X' }) return false
        val updated = getPrefixes(ctx).toMutableSet()
        updated.add(tokens)
        prefs(ctx).edit().putStringSet(KEY_PREFIXES, updated).apply()
        return true
    }

    fun removePrefix(ctx: Context, prefix: String) {
        val updated = getPrefixes(ctx).toMutableSet()
        updated.remove(prefix)
        prefs(ctx).edit().putStringSet(KEY_PREFIXES, updated).apply()
    }
}
