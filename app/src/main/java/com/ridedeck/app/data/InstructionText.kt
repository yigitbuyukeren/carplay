package com.ridedeck.app.data

/** Tidies the turn-by-turn texts the routing service sends. */
object InstructionText {
    private val roadTypes = "Caddesi|Sokağı|Sokak|Bulvarı|Yolu|Cd\\.|Sk\\."

    /** "Ahmet Yesevi Caddesi caddesine doğru sola dönün" names the road type twice: "Ahmet Yesevi Caddesine …". */
    private val doubledType = Regex("\\b($roadTypes)\\s+(?:$roadTypes)(\\p{L}*)", RegexOption.IGNORE_CASE)

    fun clean(text: String): String =
        doubledType.replace(text) { m ->
            val first = m.groupValues[1]
            val second = m.value.substring(first.length).trim()
            // Same road type twice only; "Caddesi sokağına" is two different roads and stays as it is.
            if (second.take(first.length.coerceAtMost(4)).equals(first.take(4), ignoreCase = true)) {
                val ending = second.drop(first.length.coerceAtMost(second.length)).ifEmpty { m.groupValues[2] }
                first + ending
            } else m.value
        }.replace(Regex("\\s{2,}"), " ").trim()
}
