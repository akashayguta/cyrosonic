package com.example.hunterxmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.hunterxmusic.MainActivity
import com.example.hunterxmusic.R
import java.util.Calendar

/**
 * HUNTR home-screen widget. Three user-selected modes (tap the corner chip to
 * cycle): live synced lyrics for whatever's playing, a daily quote, or a
 * minimal clock. The playback service ticks the live line once per second;
 * the PlayerScreen seeds the lyrics + track metadata whenever they change.
 */
class HuntrWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_NEXT_MODE = "com.example.hunterxmusic.widget.NEXT_MODE"
        private const val PREFS = "huntr_widget"
        private const val KEY_MODE = "mode"

        // Live playback state, written by the app layer. Also mirrored into
        // SharedPreferences so the widget isn't empty after a process death
        // or reboot (the @Volatile statics live only as long as the process).
        private const val STATE_PREFS = "huntr_widget_state"
        private const val KEY_LAST_TITLE = "last_title"
        private const val KEY_LAST_ARTIST = "last_artist"

        @Volatile var trackTitle: String = ""
        @Volatile var trackArtist: String = ""
        @Volatile var isPlaying: Boolean = false
        @Volatile var lyricsCues: List<Pair<Long, String>> = emptyList()
        @Volatile private var lastRenderedLine: String = ""

        private fun savedTrack(context: Context): Pair<String, String> {
            val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            return (prefs.getString(KEY_LAST_TITLE, "") ?: "") to
                (prefs.getString(KEY_LAST_ARTIST, "") ?: "")
        }

        fun setNowPlaying(title: String, artist: String, cues: List<Pair<Long, String>>) {
            trackTitle = title
            trackArtist = artist
            lyricsCues = cues
            lastRenderedLine = ""
        }

        fun mode(context: Context): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MODE, 0)

        fun pushUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HuntrWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context, mode(context))
            manager.updateAppWidget(ids, views)
        }

        /** Called every ~1200ms by the playback service while music runs. */
        fun tick(context: Context, positionMs: Long, playing: Boolean) {
            isPlaying = playing
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HuntrWidget::class.java))
            if (ids.isEmpty()) return
            when (mode(context)) {
                0 -> {
                    // Live lyric line — only dispatch IPC if text actually changed
                    val cue = lyricsCues.lastOrNull { positionMs >= it.first }
                    val line = cue?.second?.takeIf { it.isNotBlank() } ?: "♪"
                    if (line == lastRenderedLine) return
                    lastRenderedLine = line
                    val views = buildViews(context, 0)
                    views.setTextViewText(R.id.widget_line, line)
                    manager.updateAppWidget(ids, views)
                    if (trackTitle.isNotBlank()) {
                        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putString(KEY_LAST_TITLE, trackTitle)
                            .putString(KEY_LAST_ARTIST, trackArtist)
                            .apply()
                    }
                }
                2 -> {
                    // Clock — refresh at minute boundaries only
                    val now = Calendar.getInstance()
                    if (now.get(Calendar.SECOND) <= 1) {
                        manager.updateAppWidget(ids, buildViews(context, 2))
                    }
                }
            }
        }

        private val QUOTES = listOf(
            "Music is what feelings sound like when they have nowhere else to go.",
            "Every song you love was once a stranger.",
            "The night is louder than the day — you just have to listen.",
            "One good chorus can carry a whole week.",
            "Hunt the sound, not the noise.",
            "Some songs find you before you find them.",
            "Louder headphones, quieter world.",
            "Your playlist knows you better than your diary does.",
            "Dance first. Think later.",
            "A repeat button is just a heart with a job.",
            "Every era of your life has a soundtrack. Update it often.",
            "The bass doesn't ask permission.",
            "Old songs are postcards from who you used to be.",
            "Sing badly. Sing loud. Sing anyway.",
            "Three minutes of the right song can change a mood, a day, a mind."
        )

        private fun buildViews(context: Context, mode: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_huntr)

            when (mode) {
                0 -> {
                    val (savedTitle, savedArtist) = savedTrack(context)
                    val title = trackTitle.ifBlank { savedTitle }
                    val artist = trackArtist.ifBlank { savedArtist }
                    views.setTextViewText(R.id.widget_title, "♪ $title — $artist")
                    views.setTextViewText(
                        R.id.widget_line,
                        if (isPlaying) "…" else if (title.isBlank()) "Nothing playing — tap to hunt"
                        else "Paused"
                    )
                    views.setTextViewText(R.id.widget_sub, "LYRICS ·")
                }
                1 -> {
                    val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                    views.setTextViewText(R.id.widget_title, "Quote of the day")
                    views.setTextViewText(R.id.widget_line, QUOTES[day % QUOTES.size])
                    views.setTextViewText(R.id.widget_sub, "QUOTES ·")
                }
                else -> {
                    val now = Calendar.getInstance()
                    val time = String.format(
                        "%02d:%02d",
                        now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)
                    )
                    val date = String.format(
                        "%s %d",
                        arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")[now.get(Calendar.MONTH)],
                        now.get(Calendar.DAY_OF_MONTH)
                    )
                    views.setTextViewText(R.id.widget_title, date)
                    views.setTextViewText(R.id.widget_line, time)
                    views.setTextViewText(R.id.widget_sub, "CLOCK ·")
                }
            }

            // Tap the body → open the app
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_line, open)
            views.setOnClickPendingIntent(R.id.widget_title, open)

            // Tap the chip → cycle mode
            val cycle = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, HuntrWidget::class.java).setAction(ACTION_NEXT_MODE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_sub, cycle)

            return views
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = buildViews(context, mode(context))
        manager.updateAppWidget(ids, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_NEXT_MODE) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val next = (prefs.getInt(KEY_MODE, 0) + 1) % 3
            prefs.edit().putInt(KEY_MODE, next).apply()
            pushUpdate(context)
        }
    }
}
