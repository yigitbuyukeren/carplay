package com.ridedeck.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import com.ridedeck.app.MainActivity

/**
 * Lets RideDeck be chosen as the phone's default digital assistant (Settings > Default apps). A Bluetooth helmet's
 * voice button and the system assist gesture then start RideDeck's own voice assistant.
 */
class MotoVoiceInteractionService : VoiceInteractionService()

class MotoVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = MotoVoiceSession(this)
}

private class MotoVoiceSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VOICE_COMMAND)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        try {
            startAssistantActivity(intent)
        } catch (_: RuntimeException) {
            context.startActivity(intent)
        }
        hide()
    }
}
