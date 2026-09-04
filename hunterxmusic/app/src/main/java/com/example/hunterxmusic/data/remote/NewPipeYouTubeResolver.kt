package com.example.hunterxmusic.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Custom downloader implementation bridging NewPipeExtractor requests
 * to the application's global OkHttpClient.
 */
class NewPipeDownloader(private val okHttpClient: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = okHttpClient.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }
}

/**
 * Singleton utility that initializes NewPipe Extractor and resolves
 * YouTube video IDs into playable streaming URLs.
 */
object NewPipeYouTubeResolver {
    private const val TAG = "NewPipeYouTubeResolver"
    private var isInitialized = false

    fun init(okHttpClient: OkHttpClient) {
        if (!isInitialized) {
            try {
                NewPipe.init(NewPipeDownloader(okHttpClient))
                isInitialized = true
                Log.d(TAG, "NewPipeExtractor initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NewPipeExtractor: ${e.message}", e)
            }
        }
    }

    suspend fun resolveStreamUrl(videoId: String, okHttpClient: OkHttpClient): String? = withContext(Dispatchers.IO) {
        try {
            init(okHttpClient)
            Log.d(TAG, "Resolving stream URL for videoId: $videoId")
            
            val streamInfo = StreamInfo.getInfo(
                NewPipe.getService(0), // 0 represents YouTube service
                "https://www.youtube.com/watch?v=$videoId"
            )
            
            // Prefer high-quality audio streams
            val audioStreams = streamInfo.audioStreams
            if (!audioStreams.isNullOrEmpty()) {
                val bestAudio = audioStreams.maxByOrNull { it.bitrate } ?: audioStreams.first()
                Log.d(TAG, "Successfully resolved audio stream: ${bestAudio.content} (bitrate: ${bestAudio.bitrate})")
                return@withContext bestAudio.content
            }
            
            // Fallback to video+audio combined progressive streams
            val videoStreams = streamInfo.videoStreams
            if (!videoStreams.isNullOrEmpty()) {
                val bestVideo = videoStreams.firstOrNull()
                Log.d(TAG, "Fallback resolved video stream: ${bestVideo?.content}")
                return@withContext bestVideo?.content
            }
            
            Log.w(TAG, "No playable streams found for videoId: $videoId")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Exception resolving stream URL for videoId $videoId: ${e.message}", e)
            null
        }
    }
}
