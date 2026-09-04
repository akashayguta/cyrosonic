package com.example.hunterxmusic.presentation.lyrics

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Story-format lyric card (1080x1350): artwork washed dark behind the active
 * line, title/artist beneath, CyroSonic brand in the corners. Saved into
 * Pictures/CyroSonic via MediaStore (no storage permission needed for our own
 * inserts) and pushed straight into the system share sheet.
 */
suspend fun shareLyricsCard(
    context: Context,
    title: String,
    artist: String,
    line: String,
    artUrl: String?,
    translatedLine: String? = null
): Boolean = withContext(Dispatchers.IO) {
    try {
        val W = 1080
        val H = 1350
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)

        // Artwork wash — cover-scaled, dimmed to a texture
        if (!artUrl.isNullOrBlank()) {
            try {
                val loader = coil.Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(hiResArt(artUrl))
                    .allowHardware(false)
                    .size(W, H)
                    .build()
                val art = loader.execute(request).drawable?.let { drawable -> drawable.toBitmap() }
                if (art != null) {
                    val scale = maxOf(W.toFloat() / art.width, H.toFloat() / art.height)
                    val dw = art.width * scale
                    val dh = art.height * scale
                    val paint = Paint().apply { alpha = 70 }
                    canvas.drawBitmap(
                        art,
                        null,
                        android.graphics.RectF((W - dw) / 2f, (H - dh) / 2f, (W + dw) / 2f, (H + dh) / 2f),
                        paint
                    )
                }
            } catch (_: Exception) { }
        }

        // Vignette so text always reads
        canvas.drawRect(
            0f, 0f, W.toFloat(), H.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, H.toFloat(),
                    intArrayOf(0x99000000.toInt(), 0x55000000.toInt(), 0xCC000000.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        )

        // Brand — top left
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.35f
        }
        canvas.drawText("CYROSONIC", 84f, 130f, brand)
        val tagline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 30f
            letterSpacing = 0.2f
        }
        canvas.drawText("cosmic chill · pure sound", 84f, 178f, tagline)

        // The lyric line — big, bold, centered
        val linePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (!translatedLine.isNullOrBlank()) 66f else 76f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val lineLayout = StaticLayout.Builder
            .obtain(line, 0, line.length, linePaint, W - 220)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(20f, 1.08f)
            .build()
        val lineY = (H - lineLayout.height) / 2f - (if (!translatedLine.isNullOrBlank()) 100f else 60f)
        canvas.save()
        canvas.translate((W - lineLayout.width) / 2f, lineY)
        lineLayout.draw(canvas)
        canvas.restore()

        var currentBottom = lineY + lineLayout.height

        // Optional Translated Lyric line
        if (!translatedLine.isNullOrBlank()) {
            val transPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE879F9.toInt()
                textSize = 46f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val transLayout = StaticLayout.Builder
                .obtain(translatedLine, 0, translatedLine.length, transPaint, W - 240)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(14f, 1.05f)
                .build()
            val transY = currentBottom + 28f
            canvas.save()
            canvas.translate((W - transLayout.width) / 2f, transY)
            transLayout.draw(canvas)
            canvas.restore()
            currentBottom = transY + transLayout.height
        }

        // Accent divider
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                W / 3f, 0f, 2 * W / 3f, 0f,
                intArrayOf(0xFF7C3AED.toInt(), 0xFFD946EF.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(W / 2f - 90f, currentBottom + 40f, W / 2f + 90f, currentBottom + 48f, 4f, 4f, accent)

        // Title / artist beneath the line
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, W / 2f, currentBottom + 130f, titlePaint)
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB3FFFFFF.toInt()
            textSize = 38f
            typeface = Typeface.SANS_SERIF
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(artist, W / 2f, currentBottom + 190f, artistPaint)

        // Persist + share
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "cyrosonic_lyrics_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CyroSonic")
        }
        val uri: Uri = context.contentResolver
            .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        withContext(Dispatchers.Main) {
            val share = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(
                    Intent.EXTRA_TEXT,
                    "\"$title\" by $artist — found on CyroSonic"
                )
            context.startActivity(
                Intent.createChooser(share, "Share Lyric Card")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        true
    } catch (_: Exception) {
        false
    }
}

private fun hiResArt(url: String?): String? {
    if (url.isNullOrBlank()) return url
    return when {
        url.contains("googleusercontent.com") -> url.replace(Regex("=w[0-9]+-h[0-9]+.*"), "=w800-h800")
        url.contains("i.ytimg.com/vi/") -> url.replace("hqdefault", "sddefault")
        else -> url
    }
}

private fun android.graphics.drawable.Drawable.toBitmap(): Bitmap? {
    return try {
        val bmp = Bitmap.createBitmap(intrinsicWidth.coerceAtLeast(1), intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bmp
    } catch (_: Exception) { null }
}
