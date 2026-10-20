package dev.ygtlab.carplay.data

/** What a dashboard card slot can show. The speed card is always there; two slots hold the rest. */
enum class DashCard { NAV, MEDIA, APPS, WIDGET, NONE }

object DashLayout {
    val DEFAULT = listOf(DashCard.NAV, DashCard.MEDIA)

    fun fromString(s: String?): List<DashCard> {
        val parsed = s?.split(',')?.mapNotNull { name -> DashCard.entries.firstOrNull { it.name == name.trim() } }.orEmpty()
        if (parsed.size != 2) return DEFAULT
        return normalize(parsed)
    }

    fun toString(slots: List<DashCard>): String = slots.joinToString(",") { it.name }

    /** A card can only be in one slot: the later slot gives way. */
    fun normalize(slots: List<DashCard>): List<DashCard> =
        slots.mapIndexed { i, c -> if (c != DashCard.NONE && slots.take(i).contains(c)) DashCard.NONE else c }

    /** Puts [card] in [index]; if the other slot already holds it, the two swap. */
    fun set(slots: List<DashCard>, index: Int, card: DashCard): List<DashCard> {
        val other = 1 - index
        val out = slots.toMutableList()
        if (card != DashCard.NONE && out[other] == card) out[other] = out[index]
        out[index] = card
        return out
    }
}
