package cloud.jjoon.workout.common.storage

import cloud.jjoon.workout.common.error.BusinessException
import cloud.jjoon.workout.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream
import java.net.URI
import java.nio.file.Path

/**
 * Private object storage reached over the S3 API (architecture 2.6, DEC-ARCH-014). Nothing here is ever handed to
 * the app; files go in and out through the API. Storage outages become 503 so the app knows it can retry.
 */
@Component
class ObjectStorage(
    @Value("\${storage.s3.endpoint}") endpoint: URI,
    @Value("\${storage.s3.bucket}") private val bucket: String,
    @Value("\${storage.s3.access-key}") accessKey: String,
    @Value("\${storage.s3.secret-key}") secretKey: String,
) {
    private val s3: S3Client = S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1) // RustFS ignores it, the SDK requires one
        .forcePathStyle(true)
        // Only send checksums S3 requires; S3-compatible stores lag behind AWS's newer checksum headers.
        .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
        .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .build()

    fun put(key: String, file: Path, contentType: String) = call {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(), RequestBody.fromFile(file))
    }

    /** Opens the object for streaming; [range] is an HTTP `Range` value passed through to the store. */
    fun open(key: String, range: String? = null): StoredObject = call {
        val request = GetObjectRequest.builder().bucket(bucket).key(key)
        if (range != null) request.range(range)
        val stream = s3.getObject(request.build())
        StoredObject(stream, stream.response().contentLength(), stream.response().contentRange())
    }

    fun keys(prefix: String): List<String> = call {
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
            .contents().map { it.key() }
    }

    /** For after-commit cleanup: a failure is logged, never thrown, and the orphan stays invisible (DEC-ARCH-016). */
    fun deleteQuietly(keys: Collection<String>) = keys.forEach { key ->
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build())
        } catch (e: SdkException) {
            log.warn("event=storage.delete_failed key={}", key, e)
        }
    }

    fun deletePrefixQuietly(prefix: String) = try {
        deleteQuietly(keys(prefix))
    } catch (e: BusinessException) {
        log.warn("event=storage.delete_failed prefix={}", prefix)
    }

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: SdkException) {
        log.error("event=storage.unavailable", e)
        throw BusinessException(ErrorCode.SERVICE_UNAVAILABLE)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ObjectStorage::class.java)
    }
}

/** [contentRange] is set when only part of the object was requested. */
data class StoredObject(val stream: InputStream, val length: Long, val contentRange: String?)
