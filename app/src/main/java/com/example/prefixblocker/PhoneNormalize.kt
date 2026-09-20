package com.example.prefixblocker

/**
 * Shared matching logic, used by both the screening service and the UI preview.
 *
 * Two kinds of entries are supported:
 *  - Plain digits: "140" blocks anything starting with 140.
 *  - Wildcard patterns: X = exactly one digit, * = any digits (or none).
 *    "080 4602 XXXX" blocks 080-4602-0000..9999 but leaves the rest of 080
 *    (Amazon/Flipkart/Blinkit delivery callers) ringing.
 *    "*4602*" blocks anything containing 4602 anywhere.
 *
 * Spaces, dashes, dots, brackets and a leading + are ignored, so
 * "080 4602 XXXX", "080-4602-xxxx" and "0804602XXXX" are the same pattern.
 */
object PhoneNormalize {

    /** Keep digits only: "+91 140-123 4567" -> "911401234567". */
    fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Normalize a user pattern: keep digits, X (one-digit wildcard) and
     * * (any-digits wildcard); drop separators. "080 4602 xxxx" -> "0804602XXXX".
     */
    fun patternTokens(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            when {
                c.isDigit() -> sb.append(c)
                c == 'X' || c == 'x' -> sb.append('X')
                c == '*' -> sb.append('*')
                c == '+' || c == ' ' || c == '-' || c == '(' ||
                    c == ')' || c == '.' || c == '/' -> {
                }
                else -> { /* ignore anything else */ }
            }
        }
        return sb.toString()
    }

    /**
     * Variants of the incoming number to try matching against.
     * Handles trunk prefixes and +91 so "140..." matches "+91-140..."/"0140..."/"91140...".
     */
    fun variants(incomingDigits: String): List<String> {
        if (incomingDigits.isEmpty()) return emptyList()
        val out = mutableListOf(incomingDigits)
        var s = incomingDigits
        // International dial prefix "00..."
        if (s.startsWith("00") && s.length > 2) {
            s = s.substring(2)
            out.add(s)
        }
        // Leading trunk zero: "0140..." -> "140..."
        if (s.startsWith("0") && s.length > 1) {
            out.add(s.trimStart('0'))
        }
        // Indian country code: "91140..." -> "140..."
        if (s.startsWith("91") && s.length > 10) {
            out.add(s.substring(2))
            out.add(s.substring(2).trimStart('0'))
        }
        // Last-10-digits fallback for full mobile numbers with STD/prefix attached.
        if (s.length > 10) {
            out.add(s.takeLast(10))
        }
        // National form covers "080..." vs "+91-80..." spellings of the same number.
        out.add(national(s))
        return out.filter { it.isNotEmpty() }.distinct()
    }

    /**
     * National form: strip 00 / 91 country code / trunk zeros.
     * "08046021234" and "+91-80-4602-1234" both become "8046021234",
     * so one pattern covers both spellings.
     */
    fun national(digits: String): String {
        var s = digits
        if (s.startsWith("00") && s.length > 2) s = s.substring(2)
        if (s.startsWith("91") && s.length > 10) s = s.substring(2)
        s = s.trimStart('0')
        return s
    }

    /** Same nationalization for patterns (wildcards preserved). */
    private fun nationalizePattern(tokens: String): String {
        var s = tokens
        if (s.startsWith("00") && s.length > 2) s = s.substring(2)
        if (s.startsWith("91") && s.length > 10) s = s.substring(2)
        var i = 0
        while (i < s.length && s[i] == '0') i++
        s = s.substring(i)
        return s
    }

    private fun wildcardToRegex(tokens: String): Regex {
        val sb = StringBuilder("^")
        for (c in tokens) {
            when (c) {
                'X' -> sb.append("\\d")
                '*' -> sb.append("\\d*")
                else -> sb.append(c) // digits only at this point
            }
        }
        return Regex(sb.toString())
    }

    private fun matchesPattern(tokens: String, candidates: List<String>): Boolean {
        if (tokens.isEmpty()) return false
        val forms = listOf(tokens, nationalizePattern(tokens))
            .filter { it.isNotEmpty() }.distinct()
        val isWildcard = tokens.any { it == 'X' || it == '*' }
        if (!isWildcard) {
            return forms.any { form -> candidates.any { it.startsWith(form) } }
        }
        // A lone "*" means block everything — honour it literally.
        val regexes = forms.map(::wildcardToRegex)
        return regexes.any { re -> candidates.any { re.containsMatchIn(it) } }
    }

    /** Returns the matching entry, or null if none matches. */
    fun findMatchingPrefix(incomingNumber: String?, prefixes: Set<String>): String? {
        if (incomingNumber.isNullOrBlank()) return null
        val digits = digitsOnly(incomingNumber)
        if (digits.isEmpty()) return null
        val candidates = variants(digits)
        // Longest pattern first so "0804602XXXX" wins over "080".
        val sorted = prefixes.sortedByDescending { patternTokens(it).length }
        for (raw in sorted) {
            val tokens = patternTokens(raw)
            if (tokens.isEmpty()) continue
            if (matchesPattern(tokens, candidates)) {
                return raw
            }
        }
        return null
    }
}
