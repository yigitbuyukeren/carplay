package dev.ygtlab.carplay.data

import java.util.Locale

/** Turkish text helpers for the voice assistant: what the recogniser heard is messy, so compare loosely. */
object Turkish {
    private val TR = Locale("tr", "TR")
    private val PUNCT = Regex("[.,!?;:\"“”()]")
    private val SPACES = Regex("\\s+")

    /** Lower case in Turkish rules (I → ı, İ → i), no punctuation, one space between words, straight apostrophes. */
    fun normalize(raw: String): String =
        raw.replace('’', '\'').replace('‘', '\'').replace('`', '\'').lowercase(TR).replace(PUNCT, " ").replace(SPACES, " ").trim()

    /** Same, with Turkish letters folded to ASCII so "Kadıköy" and "kadikoy" compare equal. */
    fun fold(raw: String): String = normalize(raw)
        .replace('ç', 'c').replace('ş', 's').replace('ğ', 'g').replace('ı', 'i').replace('ö', 'o').replace('ü', 'u').replace('â', 'a').replace('î', 'i').replace('û', 'u')

    /** Case endings and the possessive that can hang on a name ("Kadıköy'e", "Aliyi", "annemi"), longest first. */
    private val ENDINGS = listOf(
        "ndan", "nden", "nda", "nde", "nin", "nın", "nun", "nün", "yla", "yle",
        "dan", "den", "tan", "ten", "ya", "ye", "yı", "yi", "yu", "yü", "da", "de", "ta", "te",
        "nı", "ni", "nu", "nü", "na", "ne", "ın", "in", "un", "ün", "ı", "i", "u", "ü", "a", "e",
    )

    /**
     * Plausible bare names for a word with an ending, most likely first, always ending with the word itself.
     * An apostrophe marks the ending exactly ("ali'yi" → "ali"); without one every ending is only a guess.
     */
    fun nameCandidates(word: String): List<String> {
        val w = normalize(word)
        if (w.isEmpty()) return emptyList()
        if (w.contains('\'')) return listOf(w.substringBefore('\''), w.replace("'", ""))
        val out = LinkedHashSet<String>()
        for (e in ENDINGS) {
            if (w.length - e.length >= 3 && w.endsWith(e)) out.add(w.removeSuffix(e))
        }
        out.add(w)
        return out.toList()
    }

    /** 0.0 (nothing alike) .. 1.0 (same) from the edit distance of the folded texts. */
    fun similarity(a: String, b: String): Double {
        val x = fold(a)
        val y = fold(b)
        if (x.isEmpty() && y.isEmpty()) return 1.0
        if (x.isEmpty() || y.isEmpty()) return 0.0
        return 1.0 - distance(x, y).toDouble() / maxOf(x.length, y.length)
    }

    private fun distance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            System.arraycopy(cur, 0, prev, 0, prev.size)
        }
        return prev[b.length]
    }

    /**
     * Candidates that sound like [heard], best first. A candidate whose first word equals the heard word or that
     * starts with it counts as a full match; otherwise the loose similarity must reach [threshold].
     */
    fun <T> bestMatches(heard: String, items: List<T>, threshold: Double = 0.72, name: (T) -> String): List<T> {
        val stems = nameCandidates(heard).map { fold(it) }.filter { it.isNotEmpty() }
        if (stems.isEmpty()) return emptyList()
        val scored = items.map { item ->
            val n = fold(name(item))
            val first = n.substringBefore(' ')
            var best = 0.0
            for (stem in stems) {
                val s = when {
                    n == stem || first == stem -> 1.0
                    n.startsWith(stem) && stem.length >= 3 -> 0.95
                    stem.startsWith(first) && first.length >= 3 && stem.length <= first.length + 3 -> 0.9
                    else -> maxOf(similarity(stem, n), similarity(stem, first))
                }
                if (s > best) best = s
            }
            item to best
        }.filter { it.second >= threshold }
        val top = scored.maxOfOrNull { it.second } ?: return emptyList()
        // Everything within a hair of the best counts as equally good (two Alis), the rest is dropped.
        return scored.filter { it.second >= top - 0.02 }.sortedByDescending { it.second }.map { it.first }
    }

    private val ONES = mapOf(
        "sıfır" to 0, "bir" to 1, "iki" to 2, "üç" to 3, "dört" to 4, "beş" to 5, "altı" to 6, "yedi" to 7, "sekiz" to 8, "dokuz" to 9,
    )
    private val TENS = mapOf(
        "on" to 10, "yirmi" to 20, "otuz" to 30, "kırk" to 40, "elli" to 50, "altmış" to 60, "yetmiş" to 70, "seksen" to 80, "doksan" to 90,
    )

    /** "elli" → 50, "yirmi beş" → 25, "yüz" → 100, "12" → 12; null when it is not a number. */
    fun parseNumber(text: String): Int? {
        val words = normalize(text).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        words.singleOrNull()?.toIntOrNull()?.let { return it }
        var total = 0
        var seen = false
        for (w in words) {
            when {
                w == "yüz" -> { total = if (total == 0) 100 else total * 100; seen = true }
                w in TENS -> { total += TENS.getValue(w); seen = true }
                w in ONES -> { total += ONES.getValue(w); seen = true }
                w == "ve" -> {}
                else -> return null
            }
        }
        return if (seen) total else null
    }

    /** Digits spoken one by one ("sıfır beş üç altı" or "0536 …") as a dialable string, or null. */
    fun parseDigits(text: String): String? {
        val parts = normalize(text).split(' ').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val sb = StringBuilder()
        for (p in parts) {
            when {
                p.all { it.isDigit() || it == '+' } -> sb.append(p)
                p in ONES -> sb.append(ONES.getValue(p))
                else -> return null
            }
        }
        return sb.toString().takeIf { it.count(Char::isDigit) >= 3 }
    }
}
