package com.example.hunterxmusic.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.hunterxmusic.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BroadcastNotificationPayload(
    val title: String,
    val message: String,
    val imageUrl: String? = null,
    val targetTrackQuery: String? = null,
    val actionButtonText: String = "▶️ Listen Now",
    val styleType: String = "PROMO",
    val accentColorHex: String? = "#00F2FE",
    val badgeText: String? = null
)

object OwnerNotificationManager {

    private const val CHANNEL_ID_BROADCAST = "cyrosonic_owner_broadcasts"
    private const val CHANNEL_NAME_BROADCAST = "CyroSonic Updates & Trending Drops"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_BROADCAST,
                CHANNEL_NAME_BROADCAST,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Special announcements, music recommendations and new track drops"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun sendRichBroadcastNotification(
        context: Context,
        payload: BroadcastNotificationPayload
    ) = withContext(Dispatchers.IO) {
        initNotificationChannel(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Intent to launch MainActivity with track intent if provided
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            payload.targetTrackQuery?.let {
                putExtra("EXTRA_PLAY_QUERY", it)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Load image bitmap if URL provided
        var bannerBitmap: Bitmap? = null
        if (!payload.imageUrl.isNullOrBlank()) {
            try {
                val imageLoader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(payload.imageUrl)
                    .allowHardware(false)
                    .build()
                val result = (imageLoader.execute(request) as? SuccessResult)?.drawable
                bannerBitmap = (result as? BitmapDrawable)?.bitmap
            } catch (_: Exception) { }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_BROADCAST)
            .setSmallIcon(com.example.hunterxmusic.R.drawable.ic_notification)
            .setContentTitle(payload.title)
            .setContentText(payload.message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        // Apply admin custom accent color
        if (!payload.accentColorHex.isNullOrBlank()) {
            try {
                val colorInt = android.graphics.Color.parseColor(payload.accentColorHex)
                builder.setColor(colorInt)
                builder.setColorized(true)
            } catch (_: Exception) { }
        }

        // Apply admin custom badge tag
        if (!payload.badgeText.isNullOrBlank()) {
            builder.setSubText(payload.badgeText)
        }

        if (bannerBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bannerBitmap)
                    .setBigContentTitle(payload.title)
                    .setSummaryText(payload.message)
            )
            builder.setLargeIcon(bannerBitmap)
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(payload.message)
            )
        }

        // Custom action button text
        val btnLabel = if (payload.actionButtonText.isNotBlank()) payload.actionButtonText else "▶️ Listen Now"
        builder.addAction(
            android.R.drawable.ic_media_play,
            btnLabel,
            pendingIntent
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) return@withContext
            }
            if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (_: SecurityException) {
        } catch (_: Throwable) {
        }
    }
}
