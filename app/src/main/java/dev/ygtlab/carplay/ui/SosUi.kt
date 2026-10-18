package dev.ygtlab.carplay.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ygtlab.carplay.R
import dev.ygtlab.carplay.data.SosContact
import dev.ygtlab.carplay.data.SosError
import dev.ygtlab.carplay.data.SosMath
import dev.ygtlab.carplay.data.SosPhase
import dev.ygtlab.carplay.data.SosReason
import dev.ygtlab.carplay.data.SosUi
import dev.ygtlab.carplay.util.hasPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HOLD_MS = 1500L

/** Red 56dp button that needs a 1.5 second hold, with a ring that fills while held. */
@Composable
fun SosButton(onTrigger: () -> Unit, modifier: Modifier = Modifier) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MotoColors.Red)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val hold = scope.launch {
                        val startedAt = System.currentTimeMillis()
                        while (true) {
                            progress = ((System.currentTimeMillis() - startedAt) / HOLD_MS.toFloat()).coerceAtMost(1f)
                            if (progress >= 1f) {
                                onTrigger()
                                break
                            }
                            delay(16)
                        }
                    }
                    tryAwaitRelease()
                    hold.cancel()
                    progress = 0f
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            if (progress > 0f) {
                drawArc(Color.White, -90f, 360f * progress, false, Offset.Zero, Size(size.width, size.height), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Text(stringResource(R.string.sos_button_label), fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
    }
}

/** Full-screen countdown / result. Always dark red so it cannot be missed in daylight. */
@Composable
fun SosScreen(ui: SosUi, contactNames: List<String>, onCancel: () -> Unit, onDismiss: () -> Unit) {
    if (ui.phase == SosPhase.IDLE) return
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF7A0A0A)).clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        when (ui.phase) {
            SosPhase.COUNTDOWN -> Countdown(ui, contactNames, onCancel)
            SosPhase.SENT -> Result(
                title = stringResource(if (ui.reason == SosReason.TEST) R.string.sos_test_done else R.string.sos_sent),
                detail = ui.sentTo.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.sos_sent_to_fmt, it.joinToString(", ")) },
                onDismiss = onDismiss,
            )
            SosPhase.FAILED -> Result(
                title = stringResource(R.string.sos_failed),
                detail = stringResource(
                    when (ui.error) {
                        SosError.NO_CONTACTS -> R.string.sos_err_no_contacts
                        SosError.NO_PERMISSION -> R.string.sos_err_no_permission
                        else -> R.string.sos_err_send_failed
                    }
                ),
                onDismiss = onDismiss,
            )
            SosPhase.IDLE -> {}
        }
    }
}

@Composable
private fun Countdown(ui: SosUi, contactNames: List<String>, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ui.secondsLeft.toString(),
            fontSize = 140.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.8f),
        )
        Column(modifier = Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(
                    when (ui.reason) {
                        SosReason.AUTO -> R.string.sos_count_auto
                        SosReason.MANUAL -> R.string.sos_count_manual
                        SosReason.TEST -> R.string.sos_count_test
                    }
                ),
                fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White,
            )
            if (ui.reason != SosReason.TEST) {
                Text(
                    stringResource(if (ui.reason == SosReason.AUTO) R.string.sos_count_hint_auto else R.string.sos_count_hint_manual),
                    fontSize = 18.sp, color = Color(0xE6FFFFFF),
                )
                if (contactNames.isNotEmpty()) {
                    Text(stringResource(R.string.sos_to_fmt, contactNames.joinToString(", ")), fontSize = 15.sp, color = Color(0xB3FFFFFF))
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(32.dp))
                    .background(Color.White).clickable(onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (ui.reason == SosReason.AUTO) R.string.sos_fine else R.string.sos_cancel),
                    fontSize = 40.sp, fontWeight = FontWeight.Black, color = Color(0xFF7A0A0A),
                )
            }
        }
    }
}

@Composable
private fun Result(title: String, detail: String?, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, fontSize = 20.sp, color = Color(0xE6FFFFFF), textAlign = TextAlign.Center)
        PillButton(stringResource(R.string.ok), Color.White, onDismiss, textColor = Color(0xFF7A0A0A), modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
fun SosSettingsCard(
    contacts: List<SosContact>,
    onAdd: (SosContact) -> Unit,
    onRemove: (SosContact) -> Unit,
    crashEnabled: Boolean,
    onCrashEnabled: (Boolean) -> Unit,
    buttonEnabled: Boolean,
    onButtonEnabled: (Boolean) -> Unit,
    onTest: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalMotoPalette.current
    var smsOk by remember { mutableStateOf(context.hasPermission(Manifest.permission.SEND_SMS)) }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsOk = it }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        // The picker hands over read access to just the chosen row, so no contacts permission is needed.
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) onAdd(SosContact(c.getString(0).orEmpty(), c.getString(1).orEmpty()))
        }
    }
    val ready = contacts.isNotEmpty() && smsOk

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MotoColors.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.sos_title), fontSize = 20.sp, color = palette.text, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.sos_desc), fontSize = 15.sp, color = palette.muted)
        contacts.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, fontSize = 18.sp, color = palette.text, fontWeight = FontWeight.Medium)
                    Text(c.number, fontSize = 14.sp, color = palette.muted)
                }
                PillButton(stringResource(R.string.sos_remove), palette.cardHigh, { onRemove(c) }, textColor = palette.text)
            }
        }
        if (contacts.size < SosMath.MAX_CONTACTS) {
            PillButton(stringResource(R.string.sos_add_contact), MotoColors.Blue, {
                try {
                    pickLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.sos_picker_missing, Toast.LENGTH_SHORT).show()
                }
            })
        }
        if (!smsOk) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sos_perm_title), fontSize = 18.sp, color = palette.text, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.sos_perm_desc), fontSize = 14.sp, color = palette.muted)
                }
                PillButton(stringResource(R.string.action_allow), MotoColors.Blue, { smsLauncher.launch(Manifest.permission.SEND_SMS) })
            }
        }
        if (contacts.isEmpty()) Text(stringResource(R.string.sos_no_contacts), fontSize = 14.sp, color = MotoColors.Orange)
        SosToggle(stringResource(R.string.sos_crash_title), stringResource(R.string.sos_crash_desc), crashEnabled && ready, ready, onCrashEnabled)
        SosToggle(stringResource(R.string.sos_button_title), stringResource(R.string.sos_button_desc), buttonEnabled && ready, ready, onButtonEnabled)
        PillButton(stringResource(R.string.sos_test), palette.cardHigh, onTest, textColor = palette.text)
    }
}

@Composable
private fun SosToggle(title: String, desc: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalMotoPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, color = palette.text, fontWeight = FontWeight.Medium)
            Text(desc, fontSize = 14.sp, color = palette.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
