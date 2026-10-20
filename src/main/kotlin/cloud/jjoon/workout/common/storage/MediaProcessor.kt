package cloud.jjoon.workout.common.storage

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

enum class MediaKind { PHOTO, VIDEO }

/** The formats users may upload (workout-media BR-006). */
enum class MediaFormat(val contentType: String, val kind: MediaKind) {
    JPEG("image/jpeg", MediaKind.PHOTO),
    PNG("image/png", MediaKind.PHOTO),
    HEIC("image/heic", MediaKind.PHOTO),
    MP4("video/mp4", MediaKind.VIDEO),
    MOV("video/quicktime", MediaKind.VIDEO),
}

/**
 * Reads uploaded files with the `ffprobe`/`ffmpeg` commands (DEC-ARCH-015). The format comes from the file's first
 * bytes, never from what the request claims (architecture 5).
 */
@Component
class MediaProcessor {

    fun detect(file: Path): MediaFormat? {
        val head = Files.newInputStream(file).use { it.readNBytes(12) }
        if (head.size >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()) return MediaFormat.JPEG
        if (head.size >= 8 && head.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return MediaFormat.PNG
        if (head.size < 12 || String(head, 4, 4, Charsets.ISO_8859_1) != "ftyp") return null
        return when (val brand = String(head, 8, 4, Charsets.ISO_8859_1)) {
            in HEIF_BRANDS -> MediaFormat.HEIC
            "qt  " -> MediaFormat.MOV
            else -> if (brand.startsWith("3g")) null else MediaFormat.MP4 // isom, mp41, mp42, avc1, ...
        }
    }

    fun durationSeconds(file: Path): Double? {
        val (exit, output) = run(
            "ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
            file.toString(),
        )
        return if (exit == 0) output.trim().toDoubleOrNull() else null
    }

    /**
     * JPEG with the longest side at most [maxSide]px; a video's first frame. 640 for the preview (workout-media
     * DEC-MEDIA-003), 2048 for a photo's display copy (DEC-MEDIA-010).
     */
    fun still(file: Path, out: Path, maxSide: Int): Boolean {
        val (exit, _) = run(
            "ffmpeg", "-v", "error", "-y", "-i", file.toString(), "-frames:v", "1",
            "-vf", "scale=w='min($maxSide,iw)':h='min($maxSide,ih)':force_original_aspect_ratio=decrease",
            "-f", "image2", "-c:v", "mjpeg", "-q:v", "4", out.toString(),
        )
        return exit == 0 && Files.size(out) > 0
    }

    /**
     * A video's display copy (workout-media DEC-MEDIA-010): H.264/AAC MP4, shorter side at most 720px, at most 30fps,
     * index up front so playback starts before the whole file arrives. iPhone HDR (HLG/PQ) is tone-mapped to SDR,
     * since 8-bit H.264 would otherwise show it washed out. Runs at low priority: it is background work.
     */
    fun transcode(file: Path, out: Path): Boolean {
        val scale = "scale=w='if(gt(iw,ih),-2,min(720,iw))':h='if(gt(iw,ih),min(720,ih),-2)'"
        val filters = if (isHdr(file)) "$scale,$TONE_MAP" else "$scale,format=yuv420p"
        val (exit, _) = run(
            "nice", "ffmpeg", "-v", "error", "-y", "-i", file.toString(), "-map", "0:v:0", "-map", "0:a:0?",
            "-vf", filters, "-fpsmax", "30", "-c:v", "libx264", "-preset", "veryfast", "-crf", "26",
            "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", "-f", "mp4", out.toString(),
            timeoutSeconds = TRANSCODE_TIMEOUT_SECONDS,
        )
        return exit == 0 && Files.size(out) > 0
    }

    private fun isHdr(file: Path): Boolean {
        val (exit, output) = run(
            "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=color_transfer",
            "-of", "default=noprint_wrappers=1:nokey=1", file.toString(),
        )
        return exit == 0 && output.trim() in HDR_TRANSFERS
    }

    private fun run(vararg command: String, timeoutSeconds: Long = TIMEOUT_SECONDS): Pair<Int, String> {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return -1 to output
        }
        return process.exitValue() to output
    }

    companion object {
        private const val TIMEOUT_SECONDS = 60L
        private const val TRANSCODE_TIMEOUT_SECONDS = 15 * 60L // a 1-minute 4K60 HEVC clip decoded in software
        private val HDR_TRANSFERS = setOf("arib-std-b67", "smpte2084")
        private const val TONE_MAP =
            "zscale=t=linear:npl=100,format=gbrpf32le,zscale=p=bt709,tonemap=hable:desat=0,zscale=t=bt709:m=bt709:r=tv,format=yuv420p"
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val HEIF_BRANDS = setOf("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1")
    }
}
