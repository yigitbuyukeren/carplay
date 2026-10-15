package dev.ygtlab.carplay.util

private val URL = Regex("""https?://\S+|www\.\S+""")
private val EMOJI = Regex("""[\p{So}\p{Cs}\p{Sk}\u200d\ufe0f]""")
private val SPACES = Regex("""\s+""")

/**
 * Shortens a chat message for the dashboard and for speech: links become [linkWord], emoji and
 * symbols are dropped (text-to-speech reads them out loud), whitespace is collapsed and the
 * result is cut at a word boundary after [maxChars].
 */
fun simplifyMessage(text: String, linkWord: String, maxChars: Int = 240): String {
    val clean = text
        .replace(URL, linkWord)
        .replace(EMOJI, "")
        .replace(SPACES, " ")
        .trim()
    if (clean.length <= maxChars) return clean
    val cut = clean.take(maxChars)
    val atWord = cut.lastIndexOf(' ').takeIf { it > maxChars / 2 } ?: maxChars
    return cut.take(atWord).trimEnd(',', '.', ' ') + "…"
}
