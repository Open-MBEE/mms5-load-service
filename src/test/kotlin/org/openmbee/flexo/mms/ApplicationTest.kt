package org.openmbee.flexo.mms

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.typesafe.config.ConfigFactory
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import java.io.File
import java.io.InputStreamReader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.time.Duration
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ApplicationTest {
    companion object {
        //MINIO settings
        val MINIO_ROOT_USER = "admintest"
        val MINIO_ROOT_PASSWORD = "admintest"
        val MINIO_PORT_NUMBER = 9000

        val testEnvConfig = createTestEnvironment {
            javaClass.classLoader.getResourceAsStream("application.conf.test")?.let { it ->
                InputStreamReader(it).use { iit ->
                    config = HoconApplicationConfig(ConfigFactory.parseReader(iit).resolve())
                }
            }
        }

        var testEnv = MapApplicationConfig(
            "jwt.audience" to testEnvConfig.config.property("jwt.audience").getString(),
            "jwt.realm" to testEnvConfig.config.property("jwt.realm").getString(),
            "jwt.domain" to testEnvConfig.config.property("jwt.domain").getString(),
            "jwt.secret" to testEnvConfig.config.property("jwt.secret").getString(),
            //add s3 params
            "s3.region" to testEnvConfig.config.property("s3.region").getString(),
            "s3.bucket" to testEnvConfig.config.property("s3.bucket").getString(),
            "s3.access_key" to testEnvConfig.config.property("s3.access_key").getString(),
            "s3.secret_key" to testEnvConfig.config.property("s3.secret_key").getString()
        )

        val minioContainer: GenericContainer<Nothing> = GenericContainer<Nothing>("quay.io/minio/minio").apply {
            val minioENVs: Map<String, String> = mapOf(
                "MINIO_PORT_NUMBER" to "${MINIO_PORT_NUMBER}",
                "MINIO_ROOT_USER" to MINIO_ROOT_USER,
                "MINIO_ROOT_PASSWORD" to MINIO_ROOT_PASSWORD
            )
            withExposedPorts(MINIO_PORT_NUMBER)
            withEnv(minioENVs)
            withCommand("server /tmp/data")
            waitingFor(
                HttpWaitStrategy()
                    .forPath("/minio/health/ready")
                    .forPort(MINIO_PORT_NUMBER)
                    .withStartupTimeout(Duration.ofSeconds(10))
            )
        }
        data class AuthStruct (
            val username: String = "",
            val groups: List<String> = listOf("")
        )

        fun authorization(auth: AuthStruct): String {
            val jwtAudience = testEnvConfig.config.property("jwt.audience").getString()
            val issuer = testEnvConfig.config.property("jwt.domain").getString()
            val secret = testEnvConfig.config.property("jwt.secret").getString()
            val expires = Date(System.currentTimeMillis() + (1 * 24 * 60 * 60 * 1000))
            return JWT.create()         //ADD "Bearer " +
                .withAudience(jwtAudience)
                .withIssuer(issuer)
                .withClaim("username", auth.username)
                .withClaim("groups", auth.groups)
                .withExpiresAt(expires)
                .sign(Algorithm.HMAC256(secret))
        }

        val adminAuth = AuthStruct("admintest", listOf("super_admins"))
        val authToken = authorization(adminAuth)

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            minioContainer.start()
            testEnv.put(
                "s3.endpoint", "http://${minioContainer.host}:${minioContainer.getMappedPort(MINIO_PORT_NUMBER)}"
            )
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            minioContainer.stop()
        }
    }
    @Test
    fun testStore() = testApplication {
        environment {
            config = testEnv
        }
        application {
            module()
        }

        Assertions.assertTrue(minioContainer.isRunning)
        val filename = "test.ttl"

        client.post("store/${filename}") {
            headers{
                append(HttpHeaders.Authorization, "Bearer $authToken")
            }
            setBody(object: OutgoingContent.WriteChannelContent() {
                override val contentType = determineContentType(filename)
                //override val contentLength = File(filename).length().toLong() ?: 0L
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    File(filename).inputStream().use { input -> channel.writeAvailable(input.readBytes())}
                }
            })
        }.apply {
            assertEquals("200 OK", this.status.toString())
            val url = this.bodyAsText()
            assertNotNull(url)
            Assertions.assertTrue(url.contains(filename))

        }
    }

    @Test
    fun testPutStore() = testApplication {
        environment {
            config = testEnv
        }
        application {
            module()
        }

        Assertions.assertTrue(minioContainer.isRunning)
        val filename = "test.ttl"

        client.put("store/some/path/${filename}") {
            headers{
                append(HttpHeaders.Authorization, "Bearer $authToken")
            }
            setBody(object: OutgoingContent.WriteChannelContent() {
                override val contentType = determineContentType(filename)
                //override val contentLength = File(filename).length().toLong() ?: 0L
                override suspend fun writeTo(channel: ByteWriteChannel) {
                    File(filename).inputStream().use { input -> channel.writeAvailable(input.readBytes())}
                }
            })
        }.apply {
            assertEquals("200 OK", this.status.toString())
            val url = this.bodyAsText()
            assertNotNull(url)
            Assertions.assertTrue(url.contains(filename))

        }
        client.get("signed/some/path/${filename}") {
            headers{
                append(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }.apply {
            assertEquals("200 OK", this.status.toString())
            val url = this.bodyAsText()
            assertNotNull(url)
            Assertions.assertTrue(url.contains(filename))
            HttpClient().use {
                it.get(url).apply {
                    val data = this.bodyAsText()
                    Assertions.assertTrue(data.equals(File(filename).inputStream().readAllBytes().toString(Charsets.UTF_8)))
                }
            }
        }
        client.get("store/some/path/${filename}") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }.apply {
            assertEquals("200 OK", this.status.toString())
            val data = this.bodyAsText()
            Assertions.assertTrue(data.equals(File(filename).inputStream().readAllBytes().toString(Charsets.UTF_8)))
        }
    }

    @Test
    fun testNotFound() = testApplication {
        environment {
            config = testEnv
        }
        application {
            module()
        }
        Assertions.assertTrue(minioContainer.isRunning)

        client.get("store/some/path/bad.txt") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }.apply {
            assertEquals(404, this.status.value)
        }
    }

    //Used to get the content type of the uploaded file
    private fun determineContentType(filePath: String): ContentType {
        val path = FileSystems.getDefault().getPath(filePath)
        return Files.probeContentType(path)?.let { ContentType.parse(it) } ?: ContentType.Application.OctetStream
    }
}

