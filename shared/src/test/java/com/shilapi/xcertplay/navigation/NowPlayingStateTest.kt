package com.shilapi.xcertplay.navigation

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingStateTest {
    private fun update(block: com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder.() -> Unit) =
        Iap2Messages.buildRaw(NowPlayingState.NOW_PLAYING_UPDATE, block)

    @Test
    fun followsTitleArtistAndPlaybackStatus() {
        val state = NowPlayingState()

        assertEquals(NowPlaying("Numb — Linkin Park", false, "Numb"),
            state.accept(update { group(0) { string(1, "Numb"); string(12, "Linkin Park") } }))
        assertEquals(NowPlaying("Numb — Linkin Park", true, "Numb"), state.accept(update { group(1) { u8(0, 1) } }))
        // Elapsed time alone changes nothing on the card.
        assertNull(state.accept(update { group(1) { u32(1, 120_706L) } }))
        assertEquals(NowPlaying("Numb — Linkin Park", false, "Numb"), state.accept(update { group(1) { u8(0, 2) } }))
        // A title-only incremental update retains the last artist.
        assertEquals(NowPlaying("Podcast — Linkin Park", false, "Podcast"), state.accept(update { group(0) { string(1, "Podcast") } }))
        assertEquals(NowPlaying("Podcast — Host", false, "Podcast"), state.accept(update { group(0) { string(12, "Host") } }))
    }

    @Test
    fun nothingWithoutATitleOrForOtherMessages() {
        val state = NowPlayingState()
        assertNull(state.accept(update { group(1) { u8(0, 1) } }))
        assertNull(state.accept(Iap2Messages.buildRaw(0x5201) { group(0) { string(1, "Numb") } }))
        assertNull(state.current())

        state.accept(update { group(0) { string(1, "Numb") } })
        state.clear()
        assertNull(state.current())
    }

    @Test
    fun clearedTitlesForgetThePreviousSongUntilANewTitleArrives() {
        val state = NowPlayingState()
        state.accept(update { group(0) { string(1, "Previous song"); string(12, "Artist") } })
        state.accept(update { group(0) { string(1, "") } })
        assertNull(state.current())
        state.accept(update { group(1) { u8(0, 1) } })
        assertNull(state.current())
        assertEquals(NowPlaying("Next song", true),
            state.accept(update { group(0) { string(1, "Next song") } }))
        state.accept(update { group(0) { string(1, "  "); string(12, "Stale artist") } })
        assertNull(state.current())
        assertEquals(NowPlaying("After clear", true),
            state.accept(update { group(0) { string(1, "After clear") } }))
    }

    @Test
    fun titleOnlyUpdatesRetainArtistAndPlaybackWhenOtherFieldsAreOmitted() {
        val state = NowPlayingState()
        state.accept(update {
            group(0) { string(1, "Track"); string(12, "Artist") }
            group(1) { u8(0, 1) }
        })
        assertEquals(NowPlaying("Lyric line — Artist", true, "Lyric line"),
            state.accept(update { group(0) { string(1, "Lyric line") } }))
        assertNull(state.accept(update { group(0) { u32(4, 180_000L) } }))
        assertEquals(NowPlaying("Lyric line — Artist", true, "Lyric line"), state.current())
    }

    @Test
    fun explicitEmptyArtistClearsItWithoutChangingTitleOrPlayback() {
        val state = NowPlayingState()
        state.accept(update { group(0) { string(1, "Track"); string(12, "Artist") } })
        assertEquals(NowPlaying("Track", false),
            state.accept(update { group(0) { string(12, "") } }))
        assertEquals(NowPlaying("Next line", false),
            state.accept(update { group(0) { string(1, "Next line") } }))
    }

    @Test
    fun completeTrackUpdateReplacesBothTitleAndArtist() {
        val state = NowPlayingState()
        state.accept(update { group(0) { string(1, "First track"); string(12, "First artist") } })
        assertEquals(NowPlaying("Second track — Second artist", false, "Second track"),
            state.accept(update { group(0) { string(1, "Second track"); string(12, "Second artist") } }))
        assertEquals(NowPlaying("Third track", false),
            state.accept(update { group(0) { string(1, "Third track"); string(12, "") } }))
    }

    @Test
    fun textJoinsTitleAndArtist() {
        assertNull(NowPlayingState.text("  ", "Artist"))
        assertEquals("Title", NowPlayingState.text(" Title ", ""))
        assertEquals("Title — Artist", NowPlayingState.text("Title", " Artist "))
    }
}
