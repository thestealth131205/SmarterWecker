package de.letheapp.nocturn.sound

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.log10
import kotlin.math.sqrt

private const val TAG = "LoudnessBooster"

private const val MIN_BOOST_MILLIBEL = 300
private const val MAX_BOOST_MILLIBEL = 1500
private const val TARGET_DBFS = -16.0
private const val QUIET_THRESHOLD_DBFS = TARGET_DBFS - (MIN_BOOST_MILLIBEL / 100.0)
private const val ANALYZE_MAX_SAMPLES = 16_000 * 3

/**
 * Hebt als leise erkannte Weckton-Clips per [LoudnessEnhancer] um mind. 3 dB an, da
 * [android.media.MediaPlayer.setVolume] nur bis zum Aufnahmepegel (0 dB) abschwächen, nie
 * darüber hinaus verstärken kann — ein leise aufgenommener Ton bliebe sonst auch bei voller
 * Lautstärke zu leise zum Aufwecken. Lautheit wird grob per RMS über die ersten ~3 Sekunden
 * dekodierter PCM-Samples geschätzt (keine echte LUFS-Messung, aber für die Boost-Entscheidung
 * ausreichend), Ergebnis pro URI gecacht, da Wecktöne wiederholt abgespielt werden. Boost ist
 * auf 15 dB gedeckelt, damit extrem leise/stille Clips nicht bis ins Rauschen hochgezogen werden.
 */
object LoudnessBooster {

    private val gainCache = ConcurrentHashMap<String, Int>()

    /** Liefert bei Bedarf einen aktivierten, zur [audioSessionId] passenden [LoudnessEnhancer], sonst null. */
    fun attach(context: Context, uri: Uri, audioSessionId: Int): LoudnessEnhancer? {
        val gainMb = gainCache.getOrPut(uri.toString()) { analyzeGainMillibel(context, uri) }
        if (gainMb <= 0) return null
        return runCatching {
            LoudnessEnhancer(audioSessionId).apply {
                setTargetGain(gainMb)
                enabled = true
            }
        }.onFailure { Log.w(TAG, "LoudnessEnhancer konnte nicht aktiviert werden für $uri", it) }
            .getOrNull()
    }

    private fun analyzeGainMillibel(context: Context, uri: Uri): Int {
        val dbfs = runCatching { measureAverageDbfs(context, uri) }
            .onFailure { Log.w(TAG, "Lautheits-Analyse fehlgeschlagen für $uri", it) }
            .getOrNull() ?: return 0
        if (dbfs >= QUIET_THRESHOLD_DBFS) return 0
        val neededDb = TARGET_DBFS - dbfs
        return (neededDb * 100).toInt().coerceIn(MIN_BOOST_MILLIBEL, MAX_BOOST_MILLIBEL)
    }

    private fun measureAverageDbfs(context: Context, uri: Uri): Double? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            val audioFormat = format ?: return null
            if (trackIndex < 0) return null
            extractor.selectTrack(trackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(audioFormat, null, null, 0)
                codec.start()
                return decodeAndMeasure(extractor, codec)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun decodeAndMeasure(extractor: MediaExtractor, codec: MediaCodec): Double? {
        var sumSquares = 0.0
        var sampleCount = 0
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        while (!sawOutputEos && sampleCount < ANALYZE_MAX_SAMPLES) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    val sampleSize = if (inBuffer != null) extractor.readSampleData(inBuffer, 0) else -1
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                if (bufferInfo.size > 0) {
                    codec.getOutputBuffer(outIndex)?.let { outBuffer ->
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        while (outBuffer.remaining() >= 2 && sampleCount < ANALYZE_MAX_SAMPLES) {
                            val lo = outBuffer.get().toInt() and 0xFF
                            val hi = outBuffer.get().toInt() and 0xFF
                            val sample = ((hi shl 8) or lo).toShort().toDouble()
                            sumSquares += sample * sample
                            sampleCount++
                        }
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEos = true
                }
            }
        }

        if (sampleCount == 0) return null
        val rms = sqrt(sumSquares / sampleCount)
        if (rms <= 0.0) return null
        return 20 * log10(rms / 32768.0)
    }
}
