package cloud.jjoon.workout

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer {
        return PostgreSQLContainer(DockerImageName.parse("postgres:18"))
    }

    /**
     * Docker Desktop sometimes drops the first connection to a container that has just reported ready (EOFException in
     * Flyway on Jenkins). Without a retry that one failure fails every test sharing the context.
     */
    @Bean
    fun flywayConnectRetries() = DynamicPropertyRegistrar { it.add("spring.flyway.connect-retries") { 5 } }

    /** The same object storage as production, reached over its S3 API (architecture 10.1). */
    @Bean
    fun rustfsContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("rustfs/rustfs:1.0.1"))
            .withEnv("RUSTFS_ACCESS_KEY", ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", SECRET_KEY)
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forHttp("/health").forPort(S3_PORT))

    @Bean
    fun storageProperties(rustfsContainer: GenericContainer<*>) = DynamicPropertyRegistrar { registry ->
        registry.add("storage.s3.endpoint") { "http://${rustfsContainer.host}:${rustfsContainer.getMappedPort(S3_PORT)}" }
        registry.add("storage.s3.access-key") { ACCESS_KEY }
        registry.add("storage.s3.secret-key") { SECRET_KEY }
    }

    companion object {
        private const val S3_PORT = 9000
        private const val ACCESS_KEY = "test-access"
        private const val SECRET_KEY = "test-secret"
    }
}
