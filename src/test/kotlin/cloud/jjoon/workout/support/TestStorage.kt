package cloud.jjoon.workout.support

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import java.net.URI

/** Creates the test bucket (production's exists already, architecture D-TODO-ARCH-007) and lets tests look inside. */
@Component
class TestStorage(
    @Value("\${storage.s3.endpoint}") endpoint: URI,
    @Value("\${storage.s3.bucket}") private val bucket: String,
    @Value("\${storage.s3.access-key}") accessKey: String,
    @Value("\${storage.s3.secret-key}") secretKey: String,
) {
    private val s3 = S3Client.builder()
        .endpointOverride(endpoint)
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .build()

    /** The container is up once its health check answers (TestcontainersConfiguration), so one call is enough. */
    @PostConstruct
    fun createBucket() {
        try {
            s3.createBucket { it.bucket(bucket) }
        } catch (e: BucketAlreadyOwnedByYouException) {
        }
    }

    fun keys(prefix: String = ""): List<String> =
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build()).contents().map { it.key() }

    fun delete(key: String) {
        s3.deleteObject { it.bucket(bucket).key(key) }
    }

    fun deleteAll() = keys().forEach(::delete)
}
