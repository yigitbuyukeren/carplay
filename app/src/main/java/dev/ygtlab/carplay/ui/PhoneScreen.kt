package dev.ygtlab.carplay.ui

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import dev.ygtlab.carplay.data.Favorite
import dev.ygtlab.carplay.data.PhoneMath
import dev.ygtlab.carplay.data.RecentCall
import java.util.Date

private enum class PhoneTab(val label: Int) {
    RECENTS(R.string.phone_recents),
    FAVORITES(R.string.phone_favorites),
    KEYPAD(R.string.phone_keypad),
}

/** In-app phone, CarPlay style: recents, starred contacts and a keypad; the keypad locks while riding. */
@Composable
fun PhoneScreen(
    recents: List<RecentCall>,
    favorites: List<Favorite>,
    hasAccess: Boolean,
    keypadLocked: Boolean,
    initialNumber: String?,
    onGrantAccess: () -> Unit,
    onDial: (String) -> Unit,
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
                        .weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp))
                        .background(if (selected) MotoColors.Green else palette.card)
                        .clickable { tab = t },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(t.label), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        color = if (selected) Color.White else palette.text,
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                tab == PhoneTab.KEYPAD -> Keypad(keypadLocked, initialNumber.orEmpty(), onDial)
                !hasAccess -> Notice(stringResource(R.string.phone_need_perm), stringResource(R.string.phone_allow), onGrantAccess)
                tab == PhoneTab.RECENTS -> RecentsList(recents, onDial)
                else -> FavoritesList(favorites, onDial)
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
private fun RecentsList(recents: List<RecentCall>, onDial: (String) -> Unit) {
    if (recents.isEmpty()) return Notice(stringResource(R.string.phone_empty_recents), null) {}
    val unknown = stringResource(R.string.phone_unknown)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
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
                    Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
                },
                onClick = { onDial(call.number) },
            )
        }
    }
}

@Composable
private fun FavoritesList(favorites: List<Favorite>, onDial: (String) -> Unit) {
    if (favorites.isEmpty()) return Notice(stringResource(R.string.phone_empty_favorites), null) {}
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(favorites, key = { it.name }) { fav ->
            ContactRow(
                title = fav.name, subtitle = fav.number, trailing = null,
                titleColor = LocalMotoPalette.current.text,
                leading = {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MotoColors.Blue), contentAlignment = Alignment.Center) {
                        Text(fav.name.take(1).uppercase(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                onClick = { onDial(fav.number) },
            )
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
            .fillMaxWidth().height(64.dp).clip(RoundedCornerShape(18.dp)).background(palette.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = palette.textSecondary, maxLines = 1)
        }
        if (trailing != null) Text(trailing, fontSize = 15.sp, color = palette.textSecondary)
        Box(Modifier.size(44.dp).clip(CircleShape).background(MotoColors.Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, stringResource(R.string.phone_call), tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

private val KEY_ROWS = listOf("123", "456", "789", "*0#")

@Composable
private fun Keypad(locked: Boolean, initial: String, onDial: (String) -> Unit) {
    var number by remember(initial) { mutableStateOf(PhoneMath.dialable(initial)) }
    val palette = LocalMotoPalette.current
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Text(
                number, fontSize = 36.sp, fontWeight = FontWeight.Bold, color = palette.text, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().height(48.dp),
            )
            if (locked) Text(stringResource(R.string.phone_keypad_locked), fontSize = 15.sp, color = MotoColors.Orange)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val canCall = !locked && PhoneMath.dialable(number).isNotEmpty()
                Box(
                    modifier = Modifier
                        .size(72.dp).clip(CircleShape).background(if (canCall) MotoColors.Green else palette.track)
                        .clickable(enabled = canCall) { onDial(number) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Call, stringResource(R.string.phone_call), tint = Color.White, modifier = Modifier.size(36.dp)) }
                Box(
                    modifier = Modifier
                        .size(56.dp).clip(CircleShape).background(palette.card)
                        .clickable(enabled = !locked && number.isNotEmpty()) { number = PhoneMath.backspace(number) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.phone_delete), tint = palette.text, modifier = Modifier.size(26.dp)) }
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            KEY_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .weight(1f).height(60.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (locked) palette.track else palette.card)
                                .clickable(enabled = !locked) { number = PhoneMath.append(number, key) },
                            contentAlignment = Alignment.Center,
                        ) { Text(key.toString(), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = if (locked) palette.muted else palette.text) }
                    }
                }
            }
        }
    }
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
