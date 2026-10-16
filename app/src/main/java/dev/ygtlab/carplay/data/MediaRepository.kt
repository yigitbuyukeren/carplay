package dev.ygtlab.carplay.data

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.MediaStore
import androidx.core.app.NotificationManagerCompat
import dev.ygtlab.carplay.service.CarNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class MediaState(
    val hasAccess: Boolean = false,
    val hasSession: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val packageName: String? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    /** SystemClock.elapsedRealtime() at which [positionMs] was sampled. */
    val positionAt: Long = 0,
    val speed: Float = 1f,
)

class MediaRepository(private val context: Context) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val listenerComponent = ComponentName(context, CarNotificationListener::class.java)

    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state

    private var controller: MediaController? = null
    private var registered = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = pick(activeControllers())
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it.orEmpty()) }

    private fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    private fun activeControllers(): List<MediaController> =
        try {
            sessions.getActiveSessions(listenerComponent)
        } catch (_: SecurityException) {
            emptyList()
        }

    fun start() {
        if (!hasAccess()) {
            _state.value = MediaState(hasAccess = false)
            return
        }
        if (!registered) {
            try {
                sessions.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent)
                registered = true
            } catch (_: SecurityException) {
                return
            }
        }
        pick(activeControllers())
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            _state.value = MediaState(hasAccess = true)
            return
        }
        val meta = c.metadata
        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        _state.value = MediaState(
            hasAccess = true,
            hasSession = true,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
            art = art,
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            packageName = c.packageName,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            positionMs = c.playbackState?.position ?: 0,
            positionAt = c.playbackState?.lastPositionUpdateTime ?: 0,
            speed = c.playbackState?.playbackSpeed ?: 1f,
        )
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    /** Asks the active music app to search and play. Returns false when no session or app can take it. */
    fun playFromSearch(query: String, fallbackPackage: String?): Boolean {
        val c = controller
        if (c != null) {
            c.transportControls.playFromSearch(query, null)
            return true
        }
        val pkg = fallbackPackage ?: return false
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(pkg)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    fun volumeUp() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    fun volumeDown() = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
}
