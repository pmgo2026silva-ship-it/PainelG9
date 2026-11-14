package br.painel.g9

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.view.KeyEvent

/** Serviço vazio: só existe para o Android liberar a leitura da música que está tocando. */
class MediaListenerService : NotificationListenerService()

class MediaHelper(private val ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mc: MediaController? = null

    var title: String? = null
    var artist: String? = null
    var playing = false
    var hasSession = false

    fun refresh() {
        mc = try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java)).firstOrNull()
        } catch (e: Exception) {
            null
        }
        val c = mc
        hasSession = c != null
        title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        artist = c?.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING
    }

    private fun key(code: Int) {
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun playPause() {
        val c = mc
        if (c != null) {
            if (playing) c.transportControls.pause() else c.transportControls.play()
        } else {
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun next() {
        val c = mc
        if (c != null) c.transportControls.skipToNext() else key(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun prev() {
        val c = mc
        if (c != null) c.transportControls.skipToPrevious() else key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun volUp() {
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
    }

    fun volDown() {
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
    }
}
