package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.body.Iap2BodyReader
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** The song CarPlay is playing; [line] is the title alone. */
internal data class NowPlaying(val text: String, val playing: Boolean, val line: String = text)

/**
 * The CarPlay song, from iAP2 NowPlayingUpdate (0x5001): title (1) and artist (12)
 * in MediaItemAttributes, playback status in PlaybackAttributes. Updates carry only what changed;
 * omitted fields retain their previous values, while an explicitly cleared title forgets the item.
 */
internal class NowPlayingState {
    private var title: String? = null
    private var artist: String? = null
    private var playing = false
    private var last: NowPlaying? = null

    /** Updates the cached card; null can mean unchanged or cleared, so consumers compare [current]. */
    fun accept(frame: Iap2Frame): NowPlaying? {
        if (frame.messageId != NOW_PLAYING_UPDATE) return null
        val body = runCatching { Iap2BodyReader.of(frame) }.getOrNull() ?: return null
        runCatching { body.optionalGroup(ITEM) }.getOrNull()?.let { item ->
            val nextTitle = runCatching { item.optionalString(TITLE) }.getOrNull()
            if (nextTitle != null) {
                title = nextTitle
                if (nextTitle.isBlank()) artist = null
            }
            if (nextTitle?.isBlank() != true) {
                runCatching { item.optionalString(ARTIST) }.getOrNull()?.let { artist = it }
            }
        }
        runCatching { body.optionalGroup(PLAYBACK)?.optionalU8(STATUS) }.getOrNull()?.let { status ->
            playing = status == STATUS_PLAYING || status == STATUS_SEEK_FORWARD || status == STATUS_SEEK_BACKWARD
        }
        val next = text(title, artist)?.let { NowPlaying(it, playing, title!!.trim()) }
        if (next == last) return null
        last = next
        return next
    }

    /** The card for the song known so far, if any. */
    fun current(): NowPlaying? = last

    /** The session ended: forget the song. */
    fun clear() {
        title = null
        artist = null
        playing = false
        last = null
    }

    companion object {
        const val NOW_PLAYING_UPDATE = 0x5001
        private const val ITEM = 0
        private const val TITLE = 1
        private const val ARTIST = 12
        private const val PLAYBACK = 1
        private const val STATUS = 0
        private const val STATUS_PLAYING = 1
        private const val STATUS_SEEK_FORWARD = 3
        private const val STATUS_SEEK_BACKWARD = 4

        /** "Title — Artist"; null without a title. */
        fun text(title: String?, artist: String?): String? {
            val name = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return artist?.trim()?.takeIf { it.isNotEmpty() }?.let { "$name — $it" } ?: name
        }
    }
}
