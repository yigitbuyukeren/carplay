package dev.ygtlab.carplay.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import dev.ygtlab.carplay.util.hasPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CallKind { INCOMING, OUTGOING, MISSED }

/** One line of the recents list; [count] is how many back-to-back calls with the same number it stands for. */
data class RecentCall(
    val number: String,
    val name: String?,
    val kind: CallKind,
    val atMs: Long,
    val count: Int = 1,
)

data class Favorite(val name: String, val number: String)

object PhoneMath {
    const val MAX_DIGITS = 20

    /** Android call log type codes: 1 incoming, 2 outgoing, 3 missed, 5 rejected, 6 blocked; voicemail (4) is skipped. */
    fun kindOf(type: Int): CallKind? = when (type) {
        1 -> CallKind.INCOMING
        2 -> CallKind.OUTGOING
        3, 5, 6 -> CallKind.MISSED
        else -> null
    }

    /** Merges runs of consecutive entries for the same number, like the system dialer; input is newest first. */
    fun collapse(calls: List<RecentCall>): List<RecentCall> {
        val out = ArrayList<RecentCall>()
        for (call in calls) {
            val last = out.lastOrNull()
            if (last != null && last.number == call.number) out[out.lastIndex] = last.copy(count = last.count + call.count)
            else out += call
        }
        return out
    }

    /** What a key press adds to the number being dialed; anything past [MAX_DIGITS] is ignored. */
    fun append(current: String, key: Char): String =
        if (key in "0123456789*#+" && current.length < MAX_DIGITS) current + key else current

    fun backspace(current: String): String = current.dropLast(1)

    /** Keeps only characters a dial string may carry; empty means there is nothing to call. */
    fun dialable(raw: String): String = raw.filter { it in "0123456789*#+" }

    /** Several phone rows can belong to one starred contact; keep the first number per name, sorted by name. */
    fun favorites(rows: List<Favorite>): List<Favorite> =
        rows.filter { it.number.isNotBlank() }.distinctBy { it.name }.sortedBy { it.name.lowercase() }
}

/** Recent calls and starred contacts from the system, plus placing a call; empty until the permissions are granted. */
class PhoneRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _recents = MutableStateFlow<List<RecentCall>>(emptyList())
    val recents: StateFlow<List<RecentCall>> = _recents
    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites

    fun canReadLog() = context.hasPermission(Manifest.permission.READ_CALL_LOG)
    fun canReadContacts() = context.hasPermission(Manifest.permission.READ_CONTACTS)

    fun refresh() {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (canReadLog()) _recents.value = safely(emptyList()) { PhoneMath.collapse(readLog()) }
                if (canReadContacts()) _favorites.value = safely(emptyList()) { PhoneMath.favorites(readStarred()) }
            }
        }
    }

    private inline fun <T> safely(fallback: T, block: () -> T): T =
        try { block() } catch (_: SecurityException) { fallback } catch (_: RuntimeException) { fallback }

    private fun readLog(): List<RecentCall> {
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, LOG_LIMIT)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }
        val cols = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE)
        val out = ArrayList<RecentCall>()
        context.contentResolver.query(CallLog.Calls.CONTENT_URI, cols, args, null)?.use { c ->
            while (c.moveToNext()) {
                val number = c.getString(0).orEmpty()
                val kind = PhoneMath.kindOf(c.getInt(2))
                if (number.isBlank() || kind == null) continue
                out += RecentCall(number, c.getString(1)?.takeIf { it.isNotBlank() }, kind, c.getLong(3))
            }
        }
        return out
    }

    private fun readStarred(): List<Favorite> {
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val out = ArrayList<Favorite>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols,
            "${ContactsContract.Contacts.STARRED}=1", null, null,
        )?.use { c ->
            while (c.moveToNext()) out += Favorite(c.getString(0).orEmpty(), c.getString(1).orEmpty())
        }
        return out
    }

    /** Places the call directly when allowed; otherwise opens the system dialer with the number filled in. */
    fun dial(raw: String) {
        val number = PhoneMath.dialable(raw)
        if (number.isEmpty()) return
        val uri = Uri.fromParts("tel", number, null)
        if (context.hasPermission(Manifest.permission.CALL_PHONE)) {
            try {
                context.getSystemService(TelecomManager::class.java).placeCall(uri, Bundle())
                return
            } catch (_: SecurityException) {
            }
        }
        context.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val LOG_LIMIT = 60
    }
}
