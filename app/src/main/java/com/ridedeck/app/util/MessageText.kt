package com.ridedeck.app.util

private val URL = Regex("""https?://\S+|www\.\S+""")
// Pictographs and arrows only: a blanket "all symbols" class also removed the degree sign ("15°C" read as "15C").
private val EMOJI = Regex("""[\x{1F000}-\x{1FAFF}\x{2190}-\x{21FF}\x{2300}-\x{23FF}\x{2600}-\x{27BF}\x{2B00}-\x{2BFF}\x{E0020}-\x{E007F}\u200d\ufe0f]""")
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
