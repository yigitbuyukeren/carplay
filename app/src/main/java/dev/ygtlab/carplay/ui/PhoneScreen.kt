package dev.ygtlab.carplay.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.composed
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.CallKind
import dev.ygtlab.carplay.data.Contact
import dev.ygtlab.carplay.data.PhoneMath
import dev.ygtlab.carplay.data.RecentCall
import java.util.Date

/** Gloved fingers: every tap target on this screen is at least this tall. */
private val TAP = 72.dp

private enum class PhoneTab(val label: Int) {
    RECENTS(R.string.phone_recents),
    CONTACTS(R.string.phone_contacts),
    KEYPAD(R.string.phone_keypad),
}

/** In-app phone, CarPlay style: recents, starred contacts and a keypad; the keypad locks while riding. */
@Composable
fun PhoneScreen(
    recents: List<RecentCall>,
    contacts: List<Contact>,
    hasAccess: Boolean,
    keypadLocked: Boolean,
    initialNumber: String?,
    dialerHint: Boolean,
    onSetDialer: () -> Unit,
    onGrantAccess: () -> Unit,
    onDial: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember(initialNumber) { mutableStateOf(if (initialNumber != null) PhoneTab.KEYPAD else PhoneTab.RECENTS) }
    val palette = LocalMotoPalette.current
    Column(modifier = modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PhoneTab.entries.forEach { t ->
                val selected = t == tab
                Box(
                    modifier = Modifier
                        .weight(1f).height(TAP).clip(RoundedCornerShape(TAP / 2))
                        .background(if (selected) MotoColors.Green else palette.card)
                        .clickable { tab = t },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(t.label), fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        color = if (selected) Color.White else palette.text,
                    )
                }
            }
        }
        if (dialerHint) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.card).padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.dialer_hint), fontSize = 16.sp, color = palette.textSecondary, modifier = Modifier.weight(1f))
                PillButton(stringResource(R.string.action_set), MotoColors.Blue, onSetDialer, horizontalPadding = 20.dp)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                tab == PhoneTab.KEYPAD -> Keypad(keypadLocked, initialNumber.orEmpty(), contacts, recents, onDial)
                !hasAccess -> Notice(stringResource(R.string.phone_need_perm), stringResource(R.string.phone_allow), onGrantAccess)
                tab == PhoneTab.RECENTS -> RecentsList(recents, onDial)
                else -> ContactsList(contacts, onDial)
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, fontSize = 18.sp, color = LocalMotoPalette.current.textSecondary)
        if (action != null) PillButton(action, MotoColors.Green, onAction)
    }
}

@Composable
private fun RecentsList(recents: List<RecentCall>, onDial: (String, String?) -> Unit) {
    if (recents.isEmpty()) return Notice(stringResource(R.string.phone_empty_recents), null) {}
    val unknown = stringResource(R.string.phone_unknown)
    PagedList { state ->
        items(recents, key = { "${it.number}/${it.atMs}" }) { call ->
            val missed = call.kind == CallKind.MISSED
            val title = (call.name ?: call.number.ifBlank { unknown }) + if (call.count > 1) " (${call.count})" else ""
            ContactRow(
                title = title,
                subtitle = if (call.name != null) call.number else null,
                trailing = whenLabel(call.atMs),
                titleColor = if (missed) MotoColors.Red else LocalMotoPalette.current.text,
                leading = {
                    val (icon, tint) = when (call.kind) {
                        CallKind.INCOMING -> Icons.AutoMirrored.Rounded.CallReceived to MotoColors.Green
                        CallKind.OUTGOING -> Icons.AutoMirrored.Rounded.CallMade to MotoColors.Blue
                        CallKind.MISSED -> Icons.AutoMirrored.Rounded.CallMissed to MotoColors.Red
                    }
                    Icon(icon, null, tint = tint, modifier = Modifier.size(32.dp))
                },
                onClick = { onDial(call.number, call.name) },
            )
        }
    }
}

/** The whole address book: starred people first under a star header, then one header per letter. */
@Composable
private fun ContactsList(contacts: List<Contact>, onDial: (String, String?) -> Unit) {
    if (contacts.isEmpty()) return Notice(stringResource(R.string.phone_empty_contacts), null) {}
    val rows = remember(contacts) {
        buildList<Any> {
            var last: Any? = null
            for (c in contacts) {
                val group: Any = if (c.starred) STARRED else PhoneMath.initialOf(c.name)
                if (group != last) { add(group); last = group }
                add(c)
            }
        }
    }
    PagedList { _ ->
        items(rows, key = { if (it is Contact) "c/${it.name}/${it.number}" else "h/$it" }) { row ->
            if (row is Contact) {
                ContactRow(
                    title = row.name, subtitle = row.number, trailing = null,
                    titleColor = LocalMotoPalette.current.text,
                    leading = {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(MotoColors.Blue), contentAlignment = Alignment.Center) {
                            Text(PhoneMath.initialOf(row.name).toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    },
                    onClick = { onDial(row.number, row.name) },
                )
            } else {
                Text(
                    if (row == STARRED) stringResource(R.string.phone_favorites) else row.toString(),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MotoColors.Blue,
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                )
            }
        }
    }
}

private const val STARRED = "starred"

/** A list with big page up / page down buttons on the right, because flicking a long list is hard with gloves. */
@Composable
private fun PagedList(content: androidx.compose.foundation.lazy.LazyListScope.(LazyListState) -> Unit) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val palette = LocalMotoPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = state, verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            content(state)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.width(56.dp)) {
            listOf(Icons.Rounded.KeyboardArrowUp to -1, Icons.Rounded.KeyboardArrowDown to 1).forEach { (icon, dir) ->
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(palette.card)
                        .pressClickable { scope.launch { state.animateScrollBy(dir * state.layoutInfo.viewportSize.height * 0.85f) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = palette.text, modifier = Modifier.size(34.dp)) }
            }
        }
    }
}

@Composable
private fun ContactRow(
    title: String,
    subtitle: String?,
    trailing: String?,
    titleColor: Color,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val palette = LocalMotoPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth().height(80.dp).clip(RoundedCornerShape(20.dp)).background(palette.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { leading() }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = palette.textSecondary, maxLines = 1)
        }
        if (trailing != null) Text(trailing, fontSize = 15.sp, color = palette.textSecondary)
        Box(Modifier.size(60.dp).clip(CircleShape).background(MotoColors.Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, stringResource(R.string.phone_call), tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

private val KEY_ROWS = listOf("123", "456", "789", "*0#")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Keypad(
    locked: Boolean,
    initial: String,
    contacts: List<Contact>,
    recents: List<RecentCall>,
    onDial: (String, String?) -> Unit,
) {
    var number by remember(initial) { mutableStateOf(PhoneMath.dialable(initial)) }
    var name by remember(initial) { mutableStateOf<String?>(null) }
    val palette = LocalMotoPalette.current
    val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
    val suggestions = remember(number, contacts, recents) { PhoneMath.matches(number, contacts, recents) }
    val canCall = !locked && PhoneMath.dialable(number).isNotEmpty()
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // What has been typed; it shrinks for long numbers and says what to do while empty.
            Box(modifier = Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.CenterStart) {
                if (number.isEmpty()) {
                    Text(stringResource(R.string.phone_enter_number), fontSize = 28.sp, color = palette.muted)
                } else {
                    Text(
                        number,
                        fontSize = when {
                            number.length <= 10 -> 44.sp
                            number.length <= 14 -> 36.sp
                            else -> 28.sp
                        },
                        fontWeight = FontWeight.Bold, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (locked) {
                Text(stringResource(R.string.phone_keypad_locked), fontSize = 15.sp, color = MotoColors.Orange)
            } else {
                // People whose number matches what is typed: a tap fills in the whole number.
                suggestions.forEach { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(palette.card)
                            .pressClickable { number = PhoneMath.dialable(c.number); name = c.name }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(c.number, fontSize = 14.sp, color = palette.textSecondary, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (number.isEmpty() && !locked) {
                    KeypadAction(Icons.Rounded.ContentPaste, stringResource(R.string.phone_paste), palette.card) {
                        val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        number = PhoneMath.dialable(text).take(PhoneMath.MAX_DIGITS)
                        name = null
                    }
                } else {
                    // Tap deletes one digit, a long press clears the lot.
                    Box(
                        modifier = Modifier
                            .size(72.dp).clip(CircleShape).background(palette.card)
                            .combinedClickable(
                                enabled = !locked && number.isNotEmpty(),
                                onClick = { number = PhoneMath.backspace(number); name = null },
                                onLongClick = { number = ""; name = null },
                            ),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.phone_delete), tint = palette.text, modifier = Modifier.size(32.dp)) }
                }
                Row(
                    modifier = Modifier
                        .weight(1f).height(72.dp).clip(RoundedCornerShape(36.dp))
                        .background(if (canCall) MotoColors.Green else palette.track)
                        .pressClickable(enabled = canCall) { onDial(number, name) },
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    Text(stringResource(R.string.phone_call), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Column(modifier = Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KEY_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f).fillMaxHeight().defaultMinSize(minHeight = 60.dp).clip(RoundedCornerShape(20.dp))
                                .background(if (locked) palette.track else palette.cardHigh)
                                .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                                .pressKey(
                                    enabled = !locked,
                                    onClick = { number = PhoneMath.append(number, key); name = null },
                                    // Hold 0 for +, as on the phone's own keypad.
                                    onLongClick = if (key == '0') ({ number = PhoneMath.append(number, '+'); name = null }) else null,
                                ),
                            contentAlignment = Alignment.Center,
                        ) { Text(key.toString(), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = if (locked) palette.muted else palette.text) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, background: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier.size(72.dp).clip(CircleShape).background(background).pressClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = LocalMotoPalette.current.text, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 11.sp, color = LocalMotoPalette.current.textSecondary)
    }
}

/** A key that squeezes while pressed, with an optional long-press action. */
@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.pressKey(enabled: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "key")
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick, onLongClick = onLongClick)
}

@Composable
private fun whenLabel(atMs: Long): String {
    val context = LocalContext.current
    return when {
        DateUtils.isToday(atMs) -> DateFormat.getTimeFormat(context).format(Date(atMs))
        DateUtils.isToday(atMs + DateUtils.DAY_IN_MILLIS) -> stringResource(R.string.phone_yesterday)
        else -> DateFormat.getDateFormat(context).format(Date(atMs))
    }
}
