package com.ridedeck.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.CallAudioState
import com.ridedeck.app.R
import com.ridedeck.app.service.MessageBridge
import com.ridedeck.app.util.Prefs
import com.ridedeck.app.util.Units
import com.ridedeck.app.util.hasPermission
import com.ridedeck.app.util.launchPackage
import com.ridedeck.app.util.simplifyMessage
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.time.LocalTime
import java.util.Date

/** [VoiceActions] on the real controllers; each piece is handed in so the view model stays the only wiring point. */
class AndroidVoiceActions(
    private val context: Context,
    private val nav: NavigationController,
    private val routing: Routing,
    private val places: () -> List<SavedPlace>,
    private val savePlace: (PlaceKind, Place) -> Unit,
    private val location: StateFlow<LocationState>,
    private val phone: PhoneRepository,
    private val call: CallRepository,
    private val dialNumber: (String, String?) -> Unit,
    private val media: MediaRepository,
    private val weather: () -> WeatherSnapshot?,
    private val battery: () -> BatteryState,
    private val speedLimit: () -> Int?,
    private val setThemeMode: (ThemeMode) -> Unit,
    private val startSosCountdown: () -> Unit,
    private val showScreen: (VoiceScreen) -> Unit,
    private val showHelpScreen: () -> Unit,
    private val lastTrip: suspend () -> Trip?,
    private val changeBrightness: (Int) -> Unit,
    private val warningVoice: (Boolean) -> Unit,
    /** The smoothed speed the gauge shows, km/h; the spoken answer matches the dial. */
    private val displaySpeedKmh: (() -> Int)? = null,
    /** Hourly forecast for day offset 0/1 between two hours, for "yarın / akşam hava nasıl". */
    private val outlook: (suspend (dayOffset: Int, fromHour: Int, toHour: Int) -> WeatherOutlook?)? = null,
    /** Turns the assistant's own suggestions on or off (the settings switch follows). */
    private val proactiveSwitch: ((Boolean) -> Unit)? = null,
) : VoiceActions {
    private fun t(id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun openScreen(screen: VoiceScreen) = showScreen(screen)

    override suspend fun navigateTo(candidates: List<String>): String? =
        if (nav.goToFirstMatch(candidates)) nav.ui.value.destination?.name else null

    override fun goSaved(kind: PlaceKind): String? {
        val place = places().firstOrNull { it.kind == kind }?.place ?: return null
        nav.selectPlace(place)
        return place.name
    }

    override fun stopNavigation(): Boolean {
        val s = nav.ui.value
        if (!s.navigating && s.route == null && s.destination == null) return false
        nav.stopNavigation()
        return true
    }

    override fun findStops() = nav.findFuel()

    override suspend fun findNearest(term: String, along: Boolean): String? {
        // "A cafe on the way" while guiding adds a stop; otherwise it is simply the nearest one.
        if (along && nav.ui.value.navigating) nav.addStopAhead(term)?.let { return it }
        return if (nav.goToFirstMatch(listOf(term))) nav.ui.value.destination?.name else null
    }

    override suspend fun alternativeRoute(): Boolean = nav.useAlternative()

    override fun navInfo(question: NavQuestion): String? {
        val s = nav.ui.value
        if (!s.navigating) return null
        return when (question) {
            NavQuestion.REMAINING_TIME -> t(R.string.voice_remaining_time_fmt, spokenDuration(s.remainingS))
            NavQuestion.REMAINING_DISTANCE -> t(R.string.voice_remaining_distance_fmt, Units.distance(s.remainingM))
            NavQuestion.ARRIVAL -> t(R.string.voice_arrival_fmt, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + s.remainingS * 1000L)))
            NavQuestion.NEXT_TURN -> {
                val m = s.route?.maneuvers?.getOrNull(s.maneuverIndex) ?: return null
                t(R.string.voice_next_turn_fmt, Units.distance(s.distToNextM), m.instruction.ifBlank { m.verbal })
            }
        }
    }

    private fun spokenDuration(seconds: Int): String {
        val minutes = ((seconds + 30) / 60).coerceAtLeast(1)
        return if (minutes >= 60) t(R.string.voice_hours_minutes_fmt, minutes / 60, minutes % 60) else t(R.string.voice_minutes_fmt, minutes)
    }

    override fun setAvoid(highways: Boolean?, tolls: Boolean?) {
        val o = nav.options.value
        nav.setOptions(o.copy(avoidHighways = highways ?: o.avoidHighways, avoidTolls = tolls ?: o.avoidTolls))
    }

    override fun recalculate(): Boolean = nav.recalculate()

    override suspend fun saveHere(kind: PlaceKind): String? {
        val fix = location.value
        if (fix.fixAtMs <= 0) return null
        val point = GeoPoint(fix.lat, fix.lon)
        val named = routing.reverse(point).getOrNull()
        val place = Place(named?.name ?: t(R.string.selected_place), named?.detail.orEmpty(), point)
        savePlace(kind, place)
        return place.name
    }

    override fun canReadContacts(): Boolean = phone.canReadContacts()
    override fun contacts(): List<Contact> = phone.contacts.value
    override fun recents(): List<RecentCall> = phone.recents.value
    override fun dial(number: String, name: String?) = dialNumber(number, name)
    override fun callPhase(): CallPhase = call.state.value.phase
    override fun answerCall() = call.answer()
    override fun rejectCall() = call.hangUp()
    override fun muteCall() = call.toggleMute()
    override fun setSpeaker(on: Boolean) =
        call.setRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)

    override fun lastMessage(): MessageInfo? {
        val m = MessageBridge.latest.value ?: return null
        return MessageInfo(m.sender.ifBlank { m.app }, m.app, simplifyMessage(m.text, t(R.string.message_link)), m.canReply)
    }

    override fun reply(text: String): Boolean = MessageBridge.latest.value?.sendReply(context, text) == true

    override fun mediaPlay() { if (!media.isPlaying) media.togglePlay() }
    override fun mediaPause() { if (media.isPlaying) media.pause() }
    override fun mediaNext() = media.next()
    override fun mediaPrevious() = media.previous()
    override fun volumeUp() = media.volumeUp()
    override fun volumeDown() = media.volumeDown()
    override fun volumeSet(percent: Int) = media.setVolumePercent(percent)
    override fun playSearch(query: String): Boolean = media.playSearch(query)

    override suspend fun info(question: InfoQuestion): String? = when (question) {
        InfoQuestion.WEATHER -> weather()?.let { w ->
            val rain = when {
                w.rainInMin == null -> t(R.string.voice_no_rain)
                w.rainInMin == 0 -> t(R.string.voice_raining)
                else -> t(R.string.voice_rain_in_fmt, w.rainInMin)
            }
            t(R.string.voice_weather_fmt, w.tempC) + ", " + rain
        }
        InfoQuestion.WEATHER_TOMORROW -> outlookText(R.string.voice_tomorrow_word, 1, 8, 20)
        InfoQuestion.WEATHER_EVENING -> LocalTime.now().hour.let { h -> if (h >= 22) null else outlookText(R.string.voice_evening_word, 0, maxOf(17, h), 23) }
        InfoQuestion.TIME -> t(R.string.voice_time_fmt, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()))
        InfoQuestion.BATTERY -> battery().let { b ->
            t(R.string.voice_battery_fmt, b.percent) + if (b.charging) ", " + t(R.string.voice_charging) else ""
        }
        InfoQuestion.SPEED -> {
            val fix = location.value
            val fresh = System.currentTimeMillis() - fix.fixAtMs < 5000
            if (!fresh) null else t(if (Units.miles) R.string.voice_speed_mph_fmt else R.string.voice_speed_kmh_fmt, Units.speed(displaySpeedKmh?.invoke() ?: fix.speedKmh))
        }
        InfoQuestion.SPEED_LIMIT -> speedLimit()?.let { t(if (Units.miles) R.string.voice_limit_mph_fmt else R.string.voice_limit_kmh_fmt, Units.speed(it)) }
        InfoQuestion.TRIP -> lastTrip()?.let { trip ->
            val stats = trip.stats
            val avg = Units.speed(stats.avgKmh)
            val speed = t(if (Units.miles) R.string.voice_speed_short_mph_fmt else R.string.voice_speed_short_kmh_fmt, avg)
            t(R.string.voice_trip_fmt, Units.distance(stats.distanceM.toInt()), spokenDuration((trip.durationMs / 1000).toInt()), speed)
        } ?: t(R.string.voice_no_trip)
        InfoQuestion.NOW_PLAYING -> media.state.value.takeIf { it.hasSession && it.title.isNotBlank() }
            ?.let { if (it.artist.isBlank()) it.title else it.title + ", " + it.artist } ?: t(R.string.voice_nothing_playing)
    }

    private suspend fun outlookText(label: Int, dayOffset: Int, from: Int, to: Int): String? =
        outlook?.invoke(dayOffset, from, to)?.let { o -> t(R.string.voice_outlook_fmt, t(label), o.minC, o.maxC, o.rainPercent) }

    override suspend fun addStop(candidates: List<String>, saved: PlaceKind?): String? {
        if (saved != null) {
            val place = places().firstOrNull { it.kind == saved }?.place ?: return null
            return if (nav.addStop(place)) place.name else null
        }
        return nav.addStopNamed(candidates)?.name
    }

    override fun canSendSms(): Boolean = context.hasPermission(Manifest.permission.SEND_SMS)
    override fun sendSms(number: String, text: String): Boolean = AndroidSmsSender(context).send(listOf(number), text).isSuccess
    override fun nickname(key: String): Contact? = Nicknames.fromJson(Prefs.nicknames(context))[key]
    override fun setNickname(key: String, contact: Contact) {
        Prefs.setNicknames(context, Nicknames.toJson(Nicknames.fromJson(Prefs.nicknames(context)) + (key to contact)))
    }
    override fun setReminder(minutes: Int, what: String?): Boolean = Reminders.schedule(context, minutes, what)
    override fun setProactive(on: Boolean) { proactiveSwitch?.invoke(on) ?: Prefs.setVoiceProactive(context, on) }

    private val launchable: List<AppRef> by lazy {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
            .map { AppRef(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
            .filter { it.pkg != context.packageName }
    }

    override fun apps(): List<AppRef> = launchable
    override fun launchApp(pkg: String): Boolean = context.launchPackage(pkg)
    override fun setTheme(mode: ThemeMode) = setThemeMode(mode)
    override fun setMiles(miles: Boolean) = Units.set(context, miles)
    override fun setBrightness(delta: Int) = changeBrightness(delta)
    override fun setWarningVoice(on: Boolean) = warningVoice(on)
    override fun startSos() = startSosCountdown()
    override fun showHelp() = showHelpScreen()
}
